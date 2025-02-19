package net.hollowcube.posthog;

import com.google.gson.FieldNamingPolicy;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.jetbrains.annotations.Blocking;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import static net.hollowcube.posthog.PostHogNames.*;

/**
 * Represents the available API surface for PostHog. Used internally by the global {@link PostHog}.
 *
 * <p>Multiple clients may be created and managed independently.</p>
 *
 * <p>Methods are non-blocking unless otherwise specified.</p>
 */
public sealed interface PostHogClient permits PostHogClientImpl, PostHogClientNoop {

    static @NotNull PostHogClient noopPostHogClient() {
        return new PostHogClientNoop();
    }

    static @NotNull PostHogClient newPostHogClient(@NotNull String projectApiKey) {
        return new Builder(projectApiKey).build();
    }

    static @NotNull Builder newBuilder(@NotNull String projectApiKey) {
        return new Builder(projectApiKey);
    }

    @Blocking
    void shutdown(@NotNull Duration timeout);


    // Events

    /**
     * Capture an event with the given name for the given distinct ID with no properties.
     *
     * @param distinctId Unique ID of the target in your database. May not be empty.
     * @param event Name of the event. May not be empty.
     */
    default void capture(@NotNull String distinctId, @NotNull String event) {
        capture(distinctId, event, Map.of());
    }

    /**
     * Capture an event with the given name for the given distinct ID with the provided properties.
     *
     * @param distinctId Unique ID of the target in your database. May not be empty.
     * @param event Name of the event. May not be empty.
     * @param properties Event properties
     */
    default void capture(@NotNull String distinctId, @NotNull String event, @NotNull Map<String, Object> properties) {
        capture(distinctId, event, (Object) properties);
    }

    /**
     * Capture an event with the given name for the given ID with properties from the given object.
     *
     * <p>The object must be serializable to a JSON object via Gson (not primitive or array)</p>
     *
     * @param distinctId Unique ID of the target in your database. May not be empty.
     * @param event Name of the event. May not be empty.
     * @param properties Event object data
     */
    void capture(@NotNull String distinctId, @NotNull String event, @NotNull Object properties);

    /**
     * Link the given properties with the person profile of the user (distinct id).
     *
     * @param distinctId Unique ID of the target in your database. May not be empty.
     * @param properties Properties to set (including overwriting previous values) on the person profile
     * @param propertiesSetOnce Properties to set only if missing on the person profile
     */
    default void identify(@NotNull String distinctId, @Nullable Map<String, Object> properties, @Nullable Map<String, Object> propertiesSetOnce) {
        Map<String, Object> eventProps = new HashMap<>();
        if (properties != null) eventProps.put(SET, properties);
        if (propertiesSetOnce != null) eventProps.put(SET_ONCE, propertiesSetOnce);
        capture(distinctId, IDENTIFY, eventProps);
    }

    /**
     * Link the given properties with the person profile of the user (distinct id).
     *
     * <p>The objects must be serializable to a JSON object via Gson (not primitive or array)</p>
     *
     * @param distinctId Unique ID of the target in your database. May not be empty.
     * @param properties Properties to set (including overwriting previous values) on the person profile
     * @param propertiesSetOnce Properties to set only if missing on the person profile
     */
    default void identify(@NotNull String distinctId, @Nullable Object properties, @Nullable Object propertiesSetOnce) {
        Map<String, Object> eventProps = new HashMap<>();
        if (properties != null) eventProps.put(SET, properties);
        if (propertiesSetOnce != null) eventProps.put(SET_ONCE, propertiesSetOnce);
        capture(distinctId, IDENTIFY, eventProps);
    }

    /**
     * Link the given properties with the person profile of the user (distinct id).
     *
     * @param distinctId Unique ID of the target in your database. May not be empty.
     * @param properties Properties to set (including overwriting previous values) on the person profile
     */
    default void identify(@NotNull String distinctId, @Nullable Map<String, Object> properties) {
        Map<String, Object> eventProps = new HashMap<>();
        if (properties != null) eventProps.put(SET, properties);
        capture(distinctId, IDENTIFY, eventProps);
    }

    /**
     * Link the given properties with the person profile of the user (distinct id).
     *
     * <p>The object must be serializable to a JSON object via Gson (not primitive or array)</p>
     *
     * @param distinctId Unique ID of the target in your database. May not be empty.
     * @param properties Properties to set (including overwriting previous values) on the person profile
     */
    default void identify(@NotNull String distinctId, @Nullable Object properties) {
        Map<String, Object> eventProps = new HashMap<>();
        if (properties != null) eventProps.put(SET, properties);
        capture(distinctId, IDENTIFY, eventProps);
    }

    /**
     * Set the given properties with the person profile of the user (distinct id).
     *
     * @param distinctId Unique ID of the target in your database. May not be empty.
     * @param properties Properties to set (including overwriting previous values) on the person profile
     * @param propertiesSetOnce Properties to set only if missing on the person profile
     */
    default void set(@NotNull String distinctId, @Nullable Map<String, Object> properties, @Nullable Map<String, Object> propertiesSetOnce) {
        Map<String, Object> eventProps = new HashMap<>();
        if (properties != null) eventProps.put(SET, properties);
        if (propertiesSetOnce != null) eventProps.put(SET_ONCE, propertiesSetOnce);
        capture(distinctId, SET, eventProps);
    }

    /**
     * Set the given properties with the person profile of the user (distinct id).
     *
     * <p>The objects must be serializable to a JSON object via Gson (not primitive or array)</p>
     *
     * @param distinctId Unique ID of the target in your database. May not be empty.
     * @param properties Properties to set (including overwriting previous values) on the person profile
     * @param propertiesSetOnce Properties to set only if missing on the person profile
     */
    default void set(@NotNull String distinctId, @Nullable Object properties, @Nullable Object propertiesSetOnce) {
        Map<String, Object> eventProps = new HashMap<>();
        if (properties != null) eventProps.put(SET, properties);
        if (propertiesSetOnce != null) eventProps.put(SET_ONCE, propertiesSetOnce);
        capture(distinctId, SET, eventProps);
    }

    /**
     * Set the given properties with the person profile of the user (distinct id).
     *
     * @param distinctId Unique ID of the target in your database. May not be empty.
     * @param properties Properties to set (including overwriting previous values) on the person profile
     */
    default void set(@NotNull String distinctId, @Nullable Map<String, Object> properties) {
        Map<String, Object> eventProps = new HashMap<>();
        if (properties != null) eventProps.put(SET, properties);
        capture(distinctId, SET, eventProps);
    }

    /**
     * Set the given properties with the person profile of the user (distinct id).
     *
     * <p>The object must be serializable to a JSON object via Gson (not primitive or array)</p>
     *
     * @param distinctId Unique ID of the target in your database. May not be empty.
     * @param properties Properties to set (including overwriting previous values) on the person profile
     */
    default void set(@NotNull String distinctId, @Nullable Object properties) {
        Map<String, Object> eventProps = new HashMap<>();
        if (properties != null) eventProps.put(SET, properties);
        capture(distinctId, SET, eventProps);
    }

    /**
     * Alias the given distinct ID to the given alias.
     *
     * @param distinctId Unique ID of the target in your database. May not be empty.
     * @param alias Alias to set for the distinct ID. May not be empty.
     */
    default void alias(@NotNull String distinctId, @NotNull String alias) {
        capture(distinctId, CREATE_ALIAS, Map.of(
                "distinct_id", Objects.requireNonNull(distinctId),
                "alias", Objects.requireNonNull(alias)
        ));
    }

    /**
     * Assign the given properties to the given group (type &amp; key).
     *
     * @param type Group type. Must not be empty
     * @param key Group key. Must not be empty
     * @param properties Properties to set (including overwriting previous values) on the group
     */
    default void groupIdentify(@NotNull String type, @NotNull String key, @NotNull Map<String, Object> properties) {
        groupIdentify(type, key, (Object) properties);
    }

    /**
     * Assign the given properties to the given group (type &amp; key).
     *
     * <p>The object must be serializable to a JSON object via Gson (not primitive or array)</p>
     *
     * @param type Group type. Must not be empty
     * @param key Group key. Must not be empty
     * @param properties Properties to set (including overwriting previous values) on the group
     */
    default void groupIdentify(@NotNull String type, @NotNull String key, @NotNull Object properties) {
        Map<String, Object> eventProps = new HashMap<>();
        eventProps.put(GROUP_TYPE, nonNullNonEmpty("type", type));
        eventProps.put(GROUP_KEY, nonNullNonEmpty("key", key));
        eventProps.put(GROUP_SET, Objects.requireNonNull(properties));

        final String distinctId = String.format("%s_%s", type, key);
        capture(distinctId, GROUP_IDENTIFY, eventProps);
    }

    /**
     * Queue an immediate flush of the pending event queue. This call does not block on the flush to be completed.
     */
    void flush();


    // Feature flags

    /**
     * Check if the given feature flag is enabled for the given distinct ID.
     *
     * @param key Feature flag key
     * @param distinctId Unique ID of the target in your database. May not be empty
     * @return True if the feature flag is enabled for the given distinct ID, false otherwise
     */
    default boolean isFeatureEnabled(@NotNull String key, @NotNull String distinctId) {
        return getFeatureFlag(key, distinctId, null).isEnabled();
    }

    /**
     * Check if the given feature flag is enabled for the given distinct ID with extra context.
     *
     * @param key Feature flag key
     * @param distinctId Unique ID of the target in your database. May not be empty
     * @param context Extra context to pass to the feature flag evaluation
     * @return True if the feature flag is enabled for the given distinct ID, false otherwise
     */
    default boolean isFeatureEnabled(@NotNull String key, @NotNull String distinctId, @Nullable FeatureFlagContext context) {
        return getFeatureFlag(key, distinctId, context).isEnabled();
    }

    /**
     * Get the feature flag state for the given distinct ID.
     *
     * @param key Feature flag key
     * @param distinctId Unique ID of the target in your database. May not be empty
     * @return Feature flag state
     */
    default @NotNull FeatureFlagState getFeatureFlag(@NotNull String key, @NotNull String distinctId) {
        return getFeatureFlag(key, distinctId, null);
    }

    /**
     * Get the feature flag state for the given distinct ID with extra context.
     *
     * @param key Feature flag key
     * @param distinctId Unique ID of the target in your database. May not be empty
     * @param context Extra context to pass to the feature flag evaluation
     * @return Feature flag state
     */
    @NotNull FeatureFlagState getFeatureFlag(@NotNull String key, @NotNull String distinctId, @Nullable FeatureFlagContext context);

    /**
     * Get the feature flag payload for the given distinct ID.
     *
     * @param key Feature flag key
     * @param distinctId Unique ID of the target in your database. May not be empty
     * @return Feature flag payload, or null if the feature flag is disabled <i>or</i> has no payload configured.
     */
    default @Nullable String getFeatureFlagPayload(@NotNull String key, @NotNull String distinctId) {
        return getFeatureFlag(key, distinctId, null).getPayload();
    }

    /**
     * Get the feature flag payload for the given distinct ID.
     *
     * @param key Feature flag key
     * @param distinctId Unique ID of the target in your database. May not be empty
     * @param context Extra context to pass to the feature flag evaluation
     * @return Feature flag payload, or null if the feature flag is disabled <i>or</i> has no payload configured.
     */
    default @Nullable String getFeatureFlagPayload(@NotNull String key, @NotNull String distinctId, @Nullable FeatureFlagContext context) {
        return getFeatureFlag(key, distinctId, context).getPayload();
    }

    /**
     * Get all feature flags for the given distinct ID.
     *
     * @param distinctId Unique ID of the target in your database. May not be empty
     * @return Feature flag states
     */
    default @NotNull FeatureFlagStates getAllFeatureFlags(@NotNull String distinctId) {
        return getAllFeatureFlags(distinctId, null);
    }

    /**
     * Get all feature flags for the given distinct ID with extra context.
     *
     * @param distinctId Unique ID of the target in your database. May not be empty
     * @param context Extra context to pass to the feature flag evaluation
     * @return Feature flag states
     */
    @NotNull FeatureFlagStates getAllFeatureFlags(@NotNull String distinctId, @Nullable FeatureFlagContext context);

    /**
     * Triggers a full reload of all local feature flags from the remote server. Only valid when local evaluation
     * is enabled. This call does not block on the flush to be completed.
     *
     * @throws IllegalStateException if local feature flag evaluation is not enabled.
     */
    void reloadFeatureFlags();


    // Exceptions


    default void captureException(@NotNull Throwable throwable) {
        captureException(throwable, null, (Object) null);
    }

    default void captureException(@NotNull Throwable throwable, @NotNull String distinctId) {
        captureException(throwable, distinctId, (Object) null);
    }

    default void captureException(@NotNull Throwable throwable, @NotNull String distinctId, @NotNull Map<String, Object> properties) {
        captureException(throwable, distinctId, (Object) properties);
    }

    void captureException(@NotNull Throwable throwable, @Nullable String distinctId, @Nullable Object properties);


    // Client builder

    final class Builder {
        private final String projectApiKey;

        private String endpoint = "https://app.posthog.com";
        private String personalApiKey = null;

        private Duration flushInterval = Duration.ofSeconds(5);
        private int maxBatchSize = 250;
        private Map<String, Object> defaultEventProperties = new HashMap<>();
        private Duration eventBatchTimeout = Duration.ofSeconds(30);

        private boolean allowRemoteFeatureFlagEvaluation = true;
        private boolean sendFeatureFlagEvents = false;
        private Duration featureFlagsPollingInterval = Duration.ofMinutes(5);
        private Duration featureFlagsRequestTimeout = Duration.ofSeconds(3);

        private Gson gson = null; // Set on build if not overridden

        private Builder(@NotNull String projectApiKey) {
            this.projectApiKey = Objects.requireNonNull(projectApiKey);
        }

        @Contract(pure = true)
        public @NotNull Builder endpoint(@NotNull String endpoint) {
            this.endpoint = Objects.requireNonNull(endpoint);
            return this;
        }

        @Contract(pure = true)
        public @NotNull Builder personalApiKey(@NotNull String personalApiKey) {
            this.personalApiKey = Objects.requireNonNull(personalApiKey);
            return this;
        }

        @Contract(pure = true)
        public @NotNull Builder flushInterval(@NotNull Duration flushInterval) {
            if (flushInterval.isNegative())
                throw new IllegalArgumentException("Flush interval must be positive");
            this.flushInterval = Objects.requireNonNull(flushInterval);
            return this;
        }

        @Contract(pure = true)
        public @NotNull Builder maxBatchSize(int maxBatchSize) {
            if (maxBatchSize <= 0)
                throw new IllegalArgumentException("Max batch size must be positive");
            this.maxBatchSize = maxBatchSize;
            return this;
        }

        @Contract(pure = true)
        public @NotNull Builder defaultEventProperties(@NotNull Map<String, Object> defaultEventProperties) {
            this.defaultEventProperties = Objects.requireNonNull(defaultEventProperties);
            return this;
        }

        @Contract(pure = true)
        public @NotNull Builder eventBatchTimeout(@NotNull Duration eventBatchTimeout) {
            if (eventBatchTimeout.isNegative())
                throw new IllegalArgumentException("Event batch timeout must be positive");
            this.eventBatchTimeout = Objects.requireNonNull(eventBatchTimeout);
            return this;
        }

        @Contract(pure = true)
        public @NotNull Builder allowRemoteFeatureFlagEvaluation(boolean allowRemoteFeatureFlagEvaluation) {
            this.allowRemoteFeatureFlagEvaluation = allowRemoteFeatureFlagEvaluation;
            return this;
        }

        @Contract(pure = true)
        public @NotNull Builder sendFeatureFlagEvents(boolean sendFeatureFlagEvents) {
            this.sendFeatureFlagEvents = sendFeatureFlagEvents;
            return this;
        }

        @Contract(pure = true)
        public @NotNull Builder featureFlagsPollingInterval(@NotNull Duration featureFlagsPollingInterval) {
            this.featureFlagsPollingInterval = Objects.requireNonNull(featureFlagsPollingInterval);
            return this;
        }

        @Contract(pure = true)
        public @NotNull Builder featureFlagsRequestTimeout(@NotNull Duration featureFlagsRequestTimeout) {
            this.featureFlagsRequestTimeout = Objects.requireNonNull(featureFlagsRequestTimeout);
            return this;
        }

        /**
         * Allows overriding the {@link Gson} instance used for de/serializing events.
         * Can be useful for handling custom types.
         *
         * <p>The default instance will {@link GsonBuilder#disableJdkUnsafe()} and {@link GsonBuilder#setFieldNamingPolicy(FieldNamingPolicy)} {@link FieldNamingPolicy#LOWER_CASE_WITH_UNDERSCORES}</p>
         *
         * @param gson A constructed {@link Gson} instance to use for de/serialization.
         */
        @Contract(pure = true)
        public @NotNull Builder gson(@NotNull Gson gson) {
            this.gson = Objects.requireNonNull(gson);
            return this;
        }

        public @NotNull PostHogClient build() {
            var gson = Objects.requireNonNullElseGet(this.gson, () -> new GsonBuilder()
                    .disableJdkUnsafe()
                    .setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES)
                    .create());
            return new PostHogClientImpl(
                    gson,
                    endpoint, projectApiKey, personalApiKey, // API
                    flushInterval, maxBatchSize, defaultEventProperties, // Events
                    eventBatchTimeout,
                    allowRemoteFeatureFlagEvaluation, sendFeatureFlagEvents, // Feature flags
                    featureFlagsPollingInterval, featureFlagsRequestTimeout
            );
        }
    }

}
