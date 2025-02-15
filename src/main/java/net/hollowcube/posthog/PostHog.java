package net.hollowcube.posthog;

import org.jetbrains.annotations.Blocking;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

public final class PostHog {
    private static PostHogClient client = PostHogClientNoop.INSTANCE;

    public static void init(@NotNull String projectApiKey, @NotNull Function<PostHogClient.Builder, PostHogClient.Builder> builderFunc) {
        if (client != PostHogClientNoop.INSTANCE)
            throw new IllegalStateException("PostHog client already initialized");
        client = builderFunc.apply(PostHogClient.newBuilder(projectApiKey)).build();
    }

    public static @NotNull PostHogClient getClient() {
        return Objects.requireNonNull(client, "PostHog client not initialized");
    }

    @Blocking
    public static void shutdown(@NotNull Duration timeout) {
        getClient().shutdown(timeout);
    }


    // Events

    /**
     * Capture an event with the given name for the given distinct ID with no properties.
     *
     * @param distinctId Unique ID of the target in your database. May not be empty.
     * @param event Name of the event. May not be empty.
     */
    public static void capture(@NotNull String distinctId, @NotNull String event) {
        getClient().capture(distinctId, event);
    }

    /**
     * Capture an event with the given name for the given distinct ID with the provided properties.
     *
     * @param distinctId Unique ID of the target in your database. May not be empty.
     * @param event Name of the event. May not be empty.
     * @param properties Event properties
     */
    public static void capture(@NotNull String distinctId, @NotNull String event, @NotNull Map<String, Object> properties) {
        getClient().capture(distinctId, event, properties);
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
    public static void capture(@NotNull String distinctId, @NotNull String event, @NotNull Object properties) {
        getClient().capture(distinctId, event, properties);
    }

    /**
     * Link the given properties with the person profile of the user (distinct id).
     *
     * @param distinctId Unique ID of the target in your database. May not be empty.
     * @param properties Properties to set (including overwriting previous values) on the person profile
     * @param propertiesSetOnce Properties to set only if missing on the person profile
     */
    public static void identify(@NotNull String distinctId, @Nullable Map<String, Object> properties, @Nullable Map<String, Object> propertiesSetOnce) {
        getClient().identify(distinctId, properties, propertiesSetOnce);
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
    public static void identify(@NotNull String distinctId, @Nullable Object properties, @Nullable Object propertiesSetOnce) {
        getClient().identify(distinctId, properties, propertiesSetOnce);
    }

    /**
     * Link the given properties with the person profile of the user (distinct id).
     *
     * @param distinctId Unique ID of the target in your database. May not be empty.
     * @param properties Properties to set (including overwriting previous values) on the person profile
     */
    public static void identify(@NotNull String distinctId, @Nullable Map<String, Object> properties) {
        getClient().identify(distinctId, properties);
    }

    /**
     * Link the given properties with the person profile of the user (distinct id).
     *
     * <p>The object must be serializable to a JSON object via Gson (not primitive or array)</p>
     *
     * @param distinctId Unique ID of the target in your database. May not be empty.
     * @param properties Properties to set (including overwriting previous values) on the person profile
     */
    public static void identify(@NotNull String distinctId, @Nullable Object properties) {
        getClient().identify(distinctId, properties);
    }

    /**
     * Alias the given distinct ID to the given alias.
     *
     * @param distinctId Unique ID of the target in your database. May not be empty.
     * @param alias Alias to set for the distinct ID. May not be empty.
     */
    public static void alias(@NotNull String distinctId, @NotNull String alias) {
        getClient().alias(distinctId, alias);
    }

    /**
     * Queue an immediate flush of the pending event queue. This call does not block on the flush to be completed.
     */
    public static void flush() {
        getClient().flush();
    }


    // Feature flags

    /**
     * Check if the given feature flag is enabled for the given distinct ID.
     *
     * @param key Feature flag key
     * @param distinctId Unique ID of the target in your database. May not be empty
     * @return True if the feature flag is enabled for the given distinct ID, false otherwise
     */
    public static boolean isFeatureEnabled(@NotNull String key, @NotNull String distinctId) {
        return getClient().isFeatureEnabled(key, distinctId);
    }

    /**
     * Check if the given feature flag is enabled for the given distinct ID with extra context.
     *
     * @param key Feature flag key
     * @param distinctId Unique ID of the target in your database. May not be empty
     * @param context Extra context to pass to the feature flag evaluation
     * @return True if the feature flag is enabled for the given distinct ID, false otherwise
     */
    public static boolean isFeatureEnabled(@NotNull String key, @NotNull String distinctId, @Nullable FeatureFlagContext context) {
        return getClient().isFeatureEnabled(key, distinctId, context);
    }

    /**
     * Get the feature flag state for the given distinct ID.
     *
     * @param key Feature flag key
     * @param distinctId Unique ID of the target in your database. May not be empty
     * @return Feature flag state
     */
    public static @NotNull FeatureFlagState getFeatureFlag(@NotNull String key, @NotNull String distinctId) {
        return getClient().getFeatureFlag(key, distinctId);
    }

    /**
     * Get the feature flag state for the given distinct ID with extra context.
     *
     * @param key Feature flag key
     * @param distinctId Unique ID of the target in your database. May not be empty
     * @param context Extra context to pass to the feature flag evaluation
     * @return Feature flag state
     */
    public static @NotNull FeatureFlagState getFeatureFlag(@NotNull String key, @NotNull String distinctId, @Nullable FeatureFlagContext context) {
        return getClient().getFeatureFlag(key, distinctId, context);
    }

    // TODO: getFeatureFlagPayload

    /**
     * Get all feature flags for the given distinct ID.
     *
     * @param distinctId Unique ID of the target in your database. May not be empty
     * @return Feature flag states
     */
    public static @NotNull FeatureFlagStates getAllFeatureFlags(@NotNull String distinctId) {
        return getClient().getAllFeatureFlags(distinctId);
    }

    /**
     * Get all feature flags for the given distinct ID with extra context.
     *
     * @param distinctId Unique ID of the target in your database. May not be empty
     * @param context Extra context to pass to the feature flag evaluation
     * @return Feature flag states
     */
    public static @NotNull FeatureFlagStates getAllFeatureFlags(@NotNull String distinctId, @Nullable FeatureFlagContext context) {
        return getClient().getAllFeatureFlags(distinctId, context);
    }

    /**
     * Triggers a full reload of all local feature flags from the remote server. Only valid when local evaluation
     * is enabled. This call does not block on the flush to be completed.
     *
     * @throws IllegalStateException if local feature flag evaluation is not enabled.
     */
    public static void reloadFeatureFlags() {
        getClient().reloadFeatureFlags();
    }

}
