package net.hollowcube.posthog;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.Set;

public class FeatureFlagStates {
    public static final FeatureFlagStates EMPTY = new FeatureFlagStates(Map.of());

    private final Map<String, FeatureFlagState> states;

    FeatureFlagStates(@NotNull Map<String, FeatureFlagState> states) {
        this.states = states;
    }

    public @NotNull FeatureFlagState get(@NotNull String key) {
        return states.getOrDefault(key, FeatureFlagState.DISABLED);
    }

    public boolean isEnabled(@NotNull String key) {
        return get(key).isEnabled();
    }

    public @Nullable String getVariant(@NotNull String key) {
        return get(key).getVariant();
    }

    public @NotNull Set<String> keySet() {
        return states.keySet();
    }

    public @NotNull Map<String, FeatureFlagState> getStates() {
        return states;
    }

    @Override
    public String toString() {
        return states.toString();
    }
}
