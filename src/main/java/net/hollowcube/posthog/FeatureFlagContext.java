package net.hollowcube.posthog;

import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

public record FeatureFlagContext(
        @Nullable Object groups,
        @Nullable Object personProperties,
        @Nullable Map<String, Object> groupProperties,
        @Nullable Boolean sendFeatureFlagEvents, // Null will default to client setting
        @Nullable Boolean allowRemoteEvaluation // Null will default to client setting
) {
    public static final FeatureFlagContext EMPTY = new FeatureFlagContext(null, null, null, null, null);

    public static @NotNull Builder newBuilder() {
        return new Builder();
    }

    public static class Builder {
        private Object groups;
        private Object personProperties;
        private Map<String, Object> groupProperties;
        private Boolean sendFeatureFlagEvents;
        private Boolean allowRemoteEvaluation;

        private Builder() {
        }

        @Contract("_ -> this")
        public @NotNull Builder groups(@Nullable Object groups) {
            this.groups = groups;
            return this;
        }

        @Contract("_ -> this")
        public @NotNull Builder personProperties(@Nullable Object personProperties) {
            this.personProperties = personProperties;
            return this;
        }

        @Contract("_ -> this")
        public @NotNull Builder groupProperties(@Nullable Map<String, Object> groupProperties) {
            this.groupProperties = groupProperties;
            return this;
        }

        @Contract("_ -> this")
        public @NotNull Builder sendFeatureFlagEvents(@Nullable Boolean sendFeatureFlagEvents) {
            this.sendFeatureFlagEvents = sendFeatureFlagEvents;
            return this;
        }

        @Contract("_ -> this")
        public @NotNull Builder allowRemoteEvaluation(@Nullable Boolean allowRemoteEvaluation) {
            this.allowRemoteEvaluation = allowRemoteEvaluation;
            return this;
        }

        @Contract("-> new")
        public @NotNull FeatureFlagContext build() {
            return new FeatureFlagContext(groups, personProperties, groupProperties, sendFeatureFlagEvents, allowRemoteEvaluation);
        }
    }

}
