package net.hollowcube.posthog;

import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class FeatureFlagState {
    public static final FeatureFlagState ENABLED = new FeatureFlagState(true, null, null);
    public static final FeatureFlagState DISABLED = new FeatureFlagState(false, null, null);

    public static final FeatureFlagState REMOTE_EVAL_NOT_ALLOWED = new FeatureFlagState(false, null, "remote evaluation is not allowed");

    private final boolean enabled;
    private final String variant;
    private final String payload;
    private final String inconclusiveReason;

    FeatureFlagState(boolean enabled) {
        this(enabled, null, null);
    }

    FeatureFlagState(boolean enabled, @Nullable String variant, @Nullable String inconclusiveReason) {
        this.enabled = enabled;
        this.variant = variant;
        this.payload = null;
        this.inconclusiveReason = inconclusiveReason;
    }

    FeatureFlagState(@Nullable JsonObject featureFlags, @Nullable JsonObject featureFlagPayloads, @NotNull String key) {
        this.inconclusiveReason = null;

        if (featureFlags == null || !featureFlags.has(key) || !(featureFlags.get(key) instanceof JsonPrimitive value)) {
            this.enabled = false;
            this.variant = null;
            this.payload = null;
            return;
        }

        this.enabled = value.isString() || value.getAsBoolean();
        this.variant = value.isString() ? value.getAsString() : null;
        this.payload = featureFlagPayloads != null && featureFlagPayloads.has(key)
                ? featureFlagPayloads.get(key).getAsString() : null;
    }
    
    FeatureFlagState(@NotNull FeatureFlagState other, @Nullable String payload) {
        this.enabled = other.enabled;
        this.variant = other.variant;
        this.payload = payload;
        this.inconclusiveReason = other.inconclusiveReason;
    }

    /**
     * Returns true if the feature flag is enabled, false otherwise. A variant response is true.
     */
    public boolean isEnabled() {
        return this.enabled;
    }

    /**
     * Returns the variant of the feature flag if it is enabled and a variant, null otherwise.
     *
     * <p>Note that a feature flag which is enabled but not a variant will return null.</p>
     */
    public @Nullable String getVariant() {
        return this.variant;
    }

    /**
     * Returns the payload of the feature flag if it is enabled and has a payload, null otherwise.
     *
     * <p>Note that a feature flag which is enabled but has no payload will return null</p>
     */
    public @Nullable String getPayload() {
        return this.payload;
    }

    public boolean isInconclusive() {
        return this.inconclusiveReason != null;
    }

    @Override
    public String toString() {
        if (inconclusiveReason != null) {
            return String.format("inconclusive(%s)", inconclusiveReason);
        } else if (variant != null) {
            return variant;
        } else {
            return String.valueOf(enabled);
        }
    }
}
