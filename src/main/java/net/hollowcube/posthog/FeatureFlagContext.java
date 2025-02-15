package net.hollowcube.posthog;

import org.jetbrains.annotations.Nullable;

public record FeatureFlagContext(
        @Nullable Object groups,
        @Nullable Object personProperties,
        @Nullable Object groupProperties,
        @Nullable Boolean sendFeatureFlagEvents, // Null will default to client setting
        @Nullable Boolean allowRemoteEvaluation // Null will default to client setting
) {
    public static final FeatureFlagContext EMPTY = new FeatureFlagContext(null, null, null, null, null);

}
