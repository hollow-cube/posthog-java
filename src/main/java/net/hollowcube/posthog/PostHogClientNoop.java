package net.hollowcube.posthog;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Duration;

final class PostHogClientNoop implements PostHogClient {
    static final PostHogClient INSTANCE = new PostHogClientNoop();

    @Override
    public void shutdown(@NotNull Duration timeout) {

    }

    @Override
    public void capture(@NotNull String distinctId, @NotNull String event, @NotNull Object properties) {

    }

    @Override
    public void flush() {

    }

    @Override
    public @NotNull FeatureFlagState getFeatureFlag(@NotNull String key, @NotNull String distinctId, @Nullable FeatureFlagContext context) {
        return FeatureFlagState.DISABLED;
    }

    @Override
    public @NotNull FeatureFlagStates getAllFeatureFlags(@NotNull String distinctId, @Nullable FeatureFlagContext context) {
        return FeatureFlagStates.EMPTY;
    }

    @Override
    public void reloadFeatureFlags() {
    }

    @Override
    public void captureException(@NotNull Throwable throwable, @Nullable String distinctId, @Nullable Object properties) {
        
    }
}
