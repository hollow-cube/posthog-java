package net.hollowcube.posthog;

import com.google.gson.*;
import org.jetbrains.annotations.Blocking;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static net.hollowcube.posthog.FeatureFlagEvaluator.evaluateFeatureFlag;
import static net.hollowcube.posthog.FeatureFlagState.REMOTE_EVAL_NOT_ALLOWED;
import static net.hollowcube.posthog.PostHogNames.*;

public final class PostHogClientImpl implements PostHogClient {
    private static final String LIBRARY_NAME = "github.com/hollow-cube/posthog-java";
    private static final String LIBRARY_VERSION = "1.0.0";
    private static final String USER_AGENT = String.format("%s/%s", LIBRARY_NAME, LIBRARY_VERSION);
    private static final int STACKTRACE_FRAME_LIMIT = 100;

    private static final Logger log = LoggerFactory.getLogger(PostHogClientImpl.class);

    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final EventQueue queue;
    private final Timer featureFlagFetchTimer;
    private final Gson gson;

    private final String endpoint;
    private final String projectApiKey;
    private final String personalApiKey;

    private final JsonObject defaultEventProperties;
    private final Duration eventBatchTimeout;

    private Map<String, FeatureFlagsResponse.Flag> featureFlags = null; // Null until first fetch
    private final Map<String, Object> recentlyCapturedFeatureFlags = new ConcurrentHashMap<>();
    private final boolean allowRemoteFeatureFlagEvaluation;
    private final boolean sendFeatureFlagEvents;
    private final Duration featureFlagsRequestTimeout;

    PostHogClientImpl(
            @NotNull Gson gson,

            @NotNull String endpoint,
            @NotNull String projectApiKey,
            @Nullable String personalApiKey,
            // Events
            @NotNull Duration flushInterval,
            int maxBatchSize,
            @NotNull Map<String, Object> defaultEventProperties,
            @NotNull Duration eventBatchTimeout,
            // Feature flags
            boolean allowRemoteFeatureFlagEvaluation,
            boolean sendFeatureFlagEvents,
            @NotNull Duration featureFlagsPollingInterval,
            @NotNull Duration featureFlagsRequestTimeout
    ) {
        this.queue = new EventQueue(this::sendEventBatch, flushInterval, maxBatchSize);
        this.gson = gson;

        this.endpoint = endpoint;
        this.projectApiKey = projectApiKey;
        this.personalApiKey = personalApiKey;

        this.defaultEventProperties = gson.toJsonTree(defaultEventProperties).getAsJsonObject();
        this.defaultEventProperties.addProperty("$lib", LIBRARY_NAME);
        this.defaultEventProperties.addProperty("$lib_version", LIBRARY_VERSION);
        this.eventBatchTimeout = eventBatchTimeout;

        // Always enable local evaluation with personal api key.
        if (this.personalApiKey != null) {
            this.featureFlagFetchTimer = new Timer(this::loadRemoteFeatureFlags, featureFlagsPollingInterval);
        } else if (!allowRemoteFeatureFlagEvaluation) {
            throw new IllegalArgumentException("Personal API key is required when remote feature flag evaluation is disabled");
        } else this.featureFlagFetchTimer = null;
        this.allowRemoteFeatureFlagEvaluation = allowRemoteFeatureFlagEvaluation;
        this.sendFeatureFlagEvents = sendFeatureFlagEvents;
        this.featureFlagsRequestTimeout = featureFlagsRequestTimeout;
    }

    @Override
    public void shutdown(@NotNull Duration timeout) {
        try {
            this.queue.close(timeout);
            if (this.featureFlagFetchTimer != null) this.featureFlagFetchTimer.close();
            this.httpClient.shutdown();
            this.httpClient.awaitTermination(timeout);
        } catch (InterruptedException ignored) {
            // Do nothing just exit
        }
    }

    // Events

    @Override
    public void flush() {
        this.queue.flush();
    }

    @Override
    public void capture(@NotNull String distinctId, @NotNull String event, @NotNull Object properties) {
        final JsonObject eventData = new JsonObject();
        // UUID is used to deduplicate messages server side so must be unique. May need to expose this
        // as an api in the future for custom deduplication when generating.
        eventData.addProperty("uuid", UUID.randomUUID().toString());
        eventData.addProperty("timestamp", Instant.now().toString());
        eventData.addProperty("distinct_id", nonNullNonEmpty("distinctId", distinctId));
        eventData.addProperty("event", nonNullNonEmpty("event", event));

        final JsonElement localProps = gson.toJsonTree(Objects.requireNonNull(properties));
        if (!(localProps instanceof JsonObject localPropsObject))
            throw new IllegalArgumentException("Event properties must be a JSON object");
        final JsonObject eventProps = defaultEventProperties.deepCopy();
        for (final Map.Entry<String, JsonElement> entry : localPropsObject.entrySet()) {
            eventProps.add(entry.getKey(), entry.getValue());
        }
        eventData.add("properties", eventProps);

        System.out.println(new GsonBuilder().setPrettyPrinting().create().toJson(eventData));
        this.queue.enqueue(eventData);
    }

    private void sendEventBatch(@NotNull JsonArray batch) {
        final HashMap<String, Object> body = new HashMap<>();
        body.put("api_key", this.projectApiKey);
        body.put("batch", batch);

        final HttpRequest req = HttpRequest.newBuilder(URI.create(String.format("%s/batch", endpoint)))
                .POST(HttpRequest.BodyPublishers.ofString(this.gson.toJson(body)))
                .header("Content-Type", "application/json; charset=utf-8")
                .header("User-Agent", USER_AGENT)
                .timeout(eventBatchTimeout)
                .build();
        try {
            final HttpResponse<Void> res = this.httpClient.send(req, HttpResponse.BodyHandlers.discarding());
            if (res.statusCode() != 200) {
                throw new RuntimeException(String.format("unexpected response from /batch (%d)", res.statusCode()));
            }
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        } catch (HttpTimeoutException ignored) {
            log.warn("timed out making /batch request");
        } catch (Exception e) {
            // Catch everything because we do not want the queue itself to stop processing.
            log.error("failed to make /batch request", e);
        }
    }


    // Feature flags

    @Override
    public @NotNull FeatureFlagState getFeatureFlag(@NotNull String key, @NotNull String distinctId, @Nullable FeatureFlagContext context) {
        final String featureFlagKey = nonNullNonEmpty("key", key);
        final FeatureFlagContext featureFlagContext = Objects.requireNonNullElse(context, FeatureFlagContext.EMPTY);
        final boolean allowRemoteEval = featureFlagContext.allowRemoteEvaluation() != null
                ? featureFlagContext.allowRemoteEvaluation()
                : this.allowRemoteFeatureFlagEvaluation;

        // If we have local flags and this flag can be evaluated locally always prioritize that
        FeatureFlagState result = REMOTE_EVAL_NOT_ALLOWED;
        if (this.featureFlags != null) {
            final FeatureFlagsResponse.Flag flag = this.featureFlags.get(featureFlagKey);
            if (flag != null) {
                result = evaluateFeatureFlag(this.gson, flag, distinctId, featureFlagContext);
            }
        }

        // If we are allowed to eval remotely and did not get a conclusive result when doing
        // local evaluation then we should try remote evaluation.
        if (allowRemoteEval && result.isInconclusive()) {
            try {
                final JsonObject featureFlags = this.decide(distinctId, featureFlagContext)
                        .getAsJsonObject("featureFlags");
                result = new FeatureFlagState(featureFlags, featureFlagKey);
            } catch (InterruptedException ignored) {
                result = FeatureFlagState.DISABLED;
            }
        }

        // Send feature flag called event if configured to do so.
        final boolean sendCalledEvent = featureFlagContext.sendFeatureFlagEvents() != null
                ? featureFlagContext.sendFeatureFlagEvents()
                : this.sendFeatureFlagEvents;
        if (sendCalledEvent && trackCapturedFeatureFlagCall(distinctId, featureFlagKey)) {
            capture(distinctId, FEATURE_FLAG_CALLED, Map.of(
                    FEATURE_FLAG, featureFlagKey,
                    FEATURE_FLAG_RESPONSE, Objects.requireNonNullElse(result.getVariant(), String.valueOf(result.isEnabled())),
                    FEATURE_FLAG_ERRORED, result.isInconclusive()
            ));
        }

        return result;
    }

    @Override
    public @NotNull FeatureFlagStates getAllFeatureFlags(@NotNull String distinctId, @Nullable FeatureFlagContext context) {
        final FeatureFlagContext featureFlagContext = Objects.requireNonNullElse(context, FeatureFlagContext.EMPTY);
        final boolean allowRemoteEval = featureFlagContext.allowRemoteEvaluation() != null
                ? featureFlagContext.allowRemoteEvaluation()
                : this.allowRemoteFeatureFlagEvaluation;

        // First try to evaluate all of the flags locally
        boolean needsLocalEvaluation = false;
        final Map<String, FeatureFlagState> result = new HashMap<>();
        if (this.featureFlags != null) {
            for (final FeatureFlagsResponse.Flag flag : this.featureFlags.values()) {
                final FeatureFlagState state = evaluateFeatureFlag(this.gson, flag, distinctId, featureFlagContext);
                result.put(flag.key(), state);

                // If we can't resolve this flag and we _are_ allowed to do remote eval break out and do that immediately.
                if (allowRemoteEval && state.isInconclusive()) {
                    needsLocalEvaluation = true;
                    break;
                }
            }
        }
        // If we are not allowed to do remote eval we must return whatever results we got.
        // Alternatively if we succeeded in evaluating all flags we are good to go.
        if (!allowRemoteEval || !needsLocalEvaluation) {
            return new FeatureFlagStates(result);
        }

        // Evaluate the feature flags remotely
        try {
            final JsonObject featureFlags = this.decide(distinctId, featureFlagContext)
                    .getAsJsonObject("featureFlags");
            final HashMap<String, FeatureFlagState> states = new HashMap<>();
            if (featureFlags != null) {
                for (String key : featureFlags.keySet())
                    states.put(key, new FeatureFlagState(featureFlags, key));
            }
            return new FeatureFlagStates(states);
        } catch (InterruptedException ignored) {
            return FeatureFlagStates.EMPTY;
        }
    }

    @Override
    public void reloadFeatureFlags() {
        if (this.featureFlagFetchTimer == null)
            throw new UnsupportedOperationException("Local feature flag evaluation is not enabled");
        this.featureFlagFetchTimer.wakeup();
    }

    @Blocking
    private void loadRemoteFeatureFlags() {
        if (this.personalApiKey == null) return; // Sanity check

        final HttpRequest req = HttpRequest.newBuilder(URI.create(String.format("%s/api/feature_flag/local_evaluation", endpoint)))
                .header("Authorization", String.format("Bearer %s", this.personalApiKey))
                .header("User-Agent", USER_AGENT)
                .timeout(featureFlagsRequestTimeout)
                .build();
        try {
            final HttpResponse<String> res = this.httpClient.send(req, HttpResponse.BodyHandlers.ofString());
            if (res.statusCode() != 200) {
                log.error("unexpected response from /api/feature_flag/local_evaluation ({}): {}", res.statusCode(), res.body());
            }

            final FeatureFlagsResponse resBody = this.gson.fromJson(res.body(), FeatureFlagsResponse.class);
            final HashMap<String, FeatureFlagsResponse.Flag> newFeatureFlags = new HashMap<>();
            for (final FeatureFlagsResponse.Flag flag : resBody.flags()) {
                newFeatureFlags.put(flag.key(), flag);
            }
            this.featureFlags = Map.copyOf(newFeatureFlags);
        } catch (InterruptedException ignored) {
            // Do nothing just exit
        } catch (HttpTimeoutException e) {
            log.warn("timed out making /api/feature_flag/local_evaluation request", e);
        } catch (Exception e) {
            // Catch everything because we do not want the timer itself to stop running.
            log.error("failed to make /api/feature_flag/local_evaluation request", e);
        }
    }

    private @NotNull JsonObject decide(@NotNull String distinctId, @NotNull FeatureFlagContext context) throws InterruptedException {
        final HashMap<String, Object> body = new HashMap<>();
        body.put("api_key", this.projectApiKey);
        body.put("distinct_id", nonNullNonEmpty("distinctId", distinctId));
        if (context.groups() != null) body.put("groups", context.groups());
        if (context.personProperties() != null) body.put("person_properties", context.personProperties());
        if (context.groupProperties() != null) body.put("group_properties", context.groupProperties());

        final HttpRequest req = HttpRequest.newBuilder(URI.create(String.format("%s/decide?v=3", endpoint)))
                .POST(HttpRequest.BodyPublishers.ofString(this.gson.toJson(body)))
                .header("Content-Type", "application/json; charset=utf-8")
                .header("User-Agent", USER_AGENT)
                .timeout(featureFlagsRequestTimeout)
                .build();
        try {
            final HttpResponse<String> res = this.httpClient.send(req, HttpResponse.BodyHandlers.ofString());
            if (res.statusCode() != 200) {
                throw new RuntimeException(String.format("unexpected response from /decide (%d): %s",
                        res.statusCode(), res.body()));
            }

            return this.gson.fromJson(res.body(), JsonObject.class);
        } catch (HttpTimeoutException e) {
            log.warn("timed out making /decide request", e);
            return new JsonObject();
        } catch (Exception e) {
            log.error("failed to make /decide request", e);
            return new JsonObject();
        }
    }

    /**
     * Deduplicates recently sent distinctId/featureFlagKey combinations, kind of.
     *
     * <p>This concept comes from posthog-go in its <a href="https://github.com/PostHog/posthog-go/blob/ec95a60c64b0dd335dafa5c72e8a56c4edc0dbbd/posthog.go#L348">
     *     handling of sending feature flag called events.</href></a> I'm not very sure what the point is
     *     especially since the map clear allows two consecutive keys to return true.</p>
     *
     * <p>My best guess is that its simply to roughly reduce event volume when evaluating a ton of feature flags.</p>
     *
     * @return true if the distinctId/featureFlagKey combination has not been seen "recently".
     */
    private boolean trackCapturedFeatureFlagCall(@NotNull String distinctId, @NotNull String featureFlagKey) {
        if (recentlyCapturedFeatureFlags.size() > 50_000) {
            recentlyCapturedFeatureFlags.clear();
        }

        final String cacheKey = distinctId + featureFlagKey;
        return recentlyCapturedFeatureFlags.put(cacheKey, "") == null;
    }


    // Exceptions


    @Override
    public void captureException(@NotNull Throwable throwable, @Nullable String distinctId, @Nullable Object properties) {
        // this function shouldn't ever throw an error, so it logs exceptions instead of allowing them to propagate.
        // this is important to ensure we don't unexpectedly re-throw exceptions in the user's code.
        try {
            final JsonObject eventProps = properties != null ? gson.toJsonTree(properties).getAsJsonObject() : new JsonObject();

            // if there's no distinct_id, we'll generate one and set personless mode
            // via $process_person_profile = false
            if (distinctId == null) {
                eventProps.addProperty(PROCESS_PERSON_PROFILE, false);
                distinctId = UUID.randomUUID().toString();
            }
            eventProps.addProperty("$geoip_disable", true);

            eventProps.addProperty(EXCEPTION_TYPE, throwable.getClass().getSimpleName());
            eventProps.addProperty(EXCEPTION_MESSAGE, throwable.getMessage());
            eventProps.add(EXCEPTION_LIST, buildExceptionList(throwable));
            eventProps.addProperty(EXCEPTION_PERSON_URL, String.format("%s/project/%s/person/%s",
                    endpoint, projectApiKey, distinctId));

            capture(distinctId, EXCEPTION, eventProps);
        } catch (Exception e) {
            log.error("failed to capture exception", e);
        }
    }

    private @NotNull JsonArray buildExceptionList(@NotNull Throwable throwable) {
        final JsonArray exceptionList = new JsonArray();
        int parentId = -1;
        for (Throwable exc = throwable; exc != null; exc = exc.getCause()) {
            exceptionList.add(buildExceptionInterface(exc, parentId));
            parentId++;
        }
        return exceptionList;
    }

    /**
     * PostHog uses the <a href="https://develop.sentry.dev/sdk/data-model/event-payloads/exception/">Sentry exception
     * interface</a> for compatibility reasons, so convert to that.
     *
     * @return the exception interface as json
     */
    private @NotNull JsonObject buildExceptionInterface(@NotNull Throwable exc, int parentId) {
        final JsonObject exception = new JsonObject();
        exception.addProperty("type", exc.getClass().getSimpleName());
        exception.addProperty("module", exc.getClass().getPackageName());
        exception.addProperty("value", exc.getMessage());

        JsonObject mechanism = new JsonObject();
        mechanism.addProperty("type", "generic");
        mechanism.addProperty("handled", true);
        mechanism.addProperty("exception_id", parentId + 1);
        if (parentId != -1) {
            mechanism.addProperty("type", "chained");
            mechanism.addProperty("parent_id", parentId);
        }
        exception.add("mechanism", mechanism);

        JsonObject stackTrace = new JsonObject();
        stackTrace.add("frames", getStackFrames(exc.getStackTrace()));
        stackTrace.addProperty("type", "raw");
        exception.add("stacktrace", stackTrace);

        return exception;
    }

    private @NotNull JsonArray getStackFrames(StackTraceElement[] elements) {
        // Reference: https://github.com/getsentry/sentry-java/blob/9180dc53e73b588db5cb42166e4ee2dc8d3723bc/sentry/src/main/java/io/sentry/SentryStackTraceFactory.java#L30

        int startFrame = Math.max(elements.length - STACKTRACE_FRAME_LIMIT, 0);
        JsonArray stackFrames = new JsonArray();
        for (int i = elements.length - 1; i >= startFrame; i--) {
            final StackTraceElement element = elements[i];
            if (element == null) continue;

            final JsonObject frame = new JsonObject();
            // We lie and tell PostHog that this is a Python exception because they don't actually support
            // Java yet :) As far as i can tell this is only actually used for syntax highlighting in source
            // code (which we don't send) so this is probably OK for now.
            frame.addProperty("platform", "python");
            frame.addProperty("filename", element.getFileName());
            frame.addProperty("abs_path", element.getFileName());

            frame.addProperty("module", element.getClassName());
            frame.addProperty("function", element.getMethodName());

            // Protocol doesn't accept negative line numbers.
            // The runtime seem to use -2 as a way to signal a native method
            if (element.getLineNumber() >= 0)
                frame.addProperty("lineno", element.getLineNumber());

            // The following is required but we don't have this info in Java
            frame.add("pre_context", new JsonArray());
            frame.add("context_line", JsonNull.INSTANCE);
            frame.add("post_context", new JsonArray());
            frame.addProperty("in_app", true);

            stackFrames.add(frame);
        }
        return stackFrames;
    }
}
