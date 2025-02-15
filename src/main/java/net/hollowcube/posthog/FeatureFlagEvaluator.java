package net.hollowcube.posthog;


import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.hollowcube.posthog.FeatureFlagsResponse.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.TestOnly;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.PatternSyntaxException;

/**
 * Local feature flag evaluator.
 * <p>
 * Heavily based on <a href="https://github.com/PostHog/posthog-go/blob/master/featureflags.go#L342">posthog-go</a>.
 */
final class FeatureFlagEvaluator {

    static @NotNull FeatureFlagState evaluateFeatureFlag(@NotNull Gson gson, @NotNull Flag flag, @NotNull String distinctId, @NotNull FeatureFlagContext context) {
        if (flag.ensureExperienceContinuity() != null && flag.ensureExperienceContinuity()) {
            final String reason = "Feature flag " + flag.key() + " requires experience continuity, cannot be evaluated locally";
            return new FeatureFlagState(false, null, reason);
        }
        if (!flag.active()) return FeatureFlagState.DISABLED;

        final JsonObject personProperties = context.personProperties() == null ? new JsonObject() :
                gson.toJsonTree(context.personProperties()).getAsJsonObject();
        final Map<String, Object> cohorts = Map.of();

        if (flag.filters().aggregationGroupTypeIndex() != null) {
            return new FeatureFlagState(false, null, "group evaluation not yet supported"); // TODO
        }

        return matchFeatureFlagProperties(flag, distinctId, personProperties, cohorts);
    }

    private static @NotNull FeatureFlagState matchFeatureFlagProperties(
            @NotNull Flag flag, @NotNull String distinctId,
            @NotNull JsonObject personProperties, @NotNull Map<String, Object> cohorts
    ) {
        // Stable sort conditions with variant overrides to the top. This ensures that if overrides are present,
        // they are evaluated first, and the variant override is applied to the first matching condition.
        final List<Condition> conditions = new ArrayList<>(flag.filters().groups());
        conditions.sort((a, b) -> {
            int left = 1, right = 1;
            if (a.variant() != null)
                left = -1;
            if (b.variant() != null)
                right = -1;
            return left - right;
        });

        FeatureFlagState fallthrough = FeatureFlagState.DISABLED;
        for (final Condition condition : conditions) {
            final FeatureFlagState match = isConditionMatch(flag, distinctId, condition, personProperties, cohorts);
            if (match.isInconclusive()) fallthrough = match;

            if (match.isEnabled()) {
                final String variantOverride = condition.variant();
                final Variants multivariates = flag.filters().multivariate();

                if (variantOverride != null && multivariates != null && containsVariant(multivariates, variantOverride)) {
                    return new FeatureFlagState(true, variantOverride, null);
                }

                return getMatchingVariant(flag, distinctId);
            }
        }

        return fallthrough;
    }

    private static @NotNull FeatureFlagState isConditionMatch(
            @NotNull Flag flag, @NotNull String distinctId, @NotNull Condition condition,
            @NotNull JsonObject personProperties, @NotNull Map<String, Object> cohorts
    ) {
        final List<Property> properties = condition.properties();
        if (properties != null && !condition.properties().isEmpty()) {
            for (final Property property : properties) {
                final FeatureFlagState match = "cohort".equals(property.type())
                        ? matchCohort(property, personProperties, cohorts)
                        : matchProperty(property, personProperties);
                if (!match.isEnabled()) return match;
            }
        }

        // Note to future readers. We notably diverge from posthog-go and posthog-node here. Those clients do not make
        // this checkIfSimpleFlagEnabled and instead always return true if the rolloutPercentage is not null.
        // I'm not sure why they do it the way they do, but I'm pretty sure it's wrong.
        // https://github.com/PostHog/posthog-go/blob/ec95a60c64b0dd335dafa5c72e8a56c4edc0dbbd/featureflags.go#L503
        // https://github.com/PostHog/posthog-js-lite/blob/ac83f9a98806e7a4c2c32d38ea874cc31c41c645/posthog-node/src/feature-flags.ts#L330
        if (condition.rolloutPercentage() != null) {
            return checkIfSimpleFlagEnabled(flag.key(), distinctId, condition.rolloutPercentage());
        }

        return FeatureFlagState.ENABLED;
    }

    private static @NotNull FeatureFlagState matchCohort(@NotNull Property property, @NotNull JsonObject personProperties, @NotNull Map<String, Object> cohorts) {
        return new FeatureFlagState(false, null, "Cohort evaluation is not yet supported."); // TODO
    }

    @TestOnly
    static @NotNull FeatureFlagState matchProperty(@NotNull Property property, @NotNull JsonObject personProperties) {
        if (!personProperties.has(property.key())) {
            final String reason = String.format("Cannot match against property without a given value (%s)", property.key());
            return new FeatureFlagState(false, null, reason);
        }

        final var propertyValue = property.value();
        final var personValue = personProperties.get(property.key());
        return switch (property.operator()) {
            case "is_not_set" -> new FeatureFlagState(false, null, "Cannot match is_not_set operator");
            case "exact" -> new FeatureFlagState(propertyValue.isJsonArray()
                    ? contains(propertyValue.getAsJsonArray(), personValue)
                    : propertyValue.equals(personValue));
            case "is_not" -> new FeatureFlagState(propertyValue.isJsonArray()
                    ? !contains(propertyValue.getAsJsonArray(), personValue)
                    : !propertyValue.equals(personValue));
            case "is_set" -> FeatureFlagState.ENABLED;
            case "icontains" -> new FeatureFlagState(valueAsString(personValue).toLowerCase(Locale.ROOT)
                    .contains(valueAsString(propertyValue).toLowerCase(Locale.ROOT)));
            case "not_icontains" -> new FeatureFlagState(!valueAsString(personValue).toLowerCase(Locale.ROOT)
                    .contains(valueAsString(propertyValue).toLowerCase(Locale.ROOT)));
            case "regex" -> {
                try {
                    yield new FeatureFlagState(valueAsString(personValue).matches(valueAsString(propertyValue)));
                } catch (PatternSyntaxException ignored) {
                    yield FeatureFlagState.DISABLED;
                }
            }
            case "not_regex" -> {
                try {
                    yield new FeatureFlagState(!valueAsString(personValue).matches(valueAsString(propertyValue)));
                } catch (PatternSyntaxException ignored) {
                    yield FeatureFlagState.DISABLED;
                }
            }
            case "gt" ->
                    new FeatureFlagState(valueAsNumber(personValue).doubleValue() > valueAsNumber(propertyValue).doubleValue());
            case "lt" ->
                    new FeatureFlagState(valueAsNumber(personValue).doubleValue() < valueAsNumber(propertyValue).doubleValue());
            case "gte" ->
                    new FeatureFlagState(valueAsNumber(personValue).doubleValue() >= valueAsNumber(propertyValue).doubleValue());
            case "lte" ->
                    new FeatureFlagState(valueAsNumber(personValue).doubleValue() <= valueAsNumber(propertyValue).doubleValue());
            default -> new FeatureFlagState(false, null, "Unknown operator: " + property.operator());
        };
    }

    private static @NotNull FeatureFlagState getMatchingVariant(@NotNull Flag flag, @NotNull String distinctId) {
        final Variants multivariates = flag.filters().multivariate();
        if (multivariates == null || multivariates.variants() == null) {
            return FeatureFlagState.ENABLED;
        }

        double value = hash(flag.key(), distinctId, "variant"), valueMin = 0;
        for (final Variant variant : multivariates.variants()) {
            if (variant.rolloutPercentage() == null) continue;
            double valueMax = valueMin + (variant.rolloutPercentage() / 100.0);
            if (value >= valueMin && value < valueMax)
                return new FeatureFlagState(true, variant.key(), null);
            valueMin = valueMax;
        }

        return FeatureFlagState.ENABLED;
    }

    private static boolean containsVariant(@NotNull Variants variants, @NotNull String variant) {
        if (variants.variants() == null || variants.variants().isEmpty())
            return false;
        for (final var v : variants.variants())
            if (v.key().equals(variant))
                return true;
        return false;
    }

    private static @NotNull FeatureFlagState checkIfSimpleFlagEnabled(@NotNull String key, @NotNull String distinctId, int rolloutPercentage) {
        return hash(key, distinctId, "") <= (rolloutPercentage / 100.0)
                ? FeatureFlagState.ENABLED
                : FeatureFlagState.DISABLED;
    }

    // https://github.com/PostHog/posthog-go/blob/master/featureflags.go#L842
    private static double hash(@NotNull String key, @NotNull String distinctId, @NotNull String salt) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-1");
            md.update((key + "." + distinctId + salt).getBytes(StandardCharsets.UTF_8));
            byte[] digest = md.digest();
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) sb.append(String.format("%02x", b));
            var hexString = sb.substring(0, Math.min(15, sb.length()));
            return ((double) Long.parseLong(hexString, 16)) / 0xfffffffffffffffL;
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e); // Unreachable
        }
    }

    private static boolean contains(@NotNull JsonArray array, @NotNull JsonElement elem) {
        for (final var e : array)
            if (e.equals(elem))
                return true;
        return false;
    }

    private static @NotNull String valueAsString(@NotNull JsonElement elem) {
        if (elem.isJsonPrimitive()) return elem.getAsString();
        if (elem.isJsonNull()) return "null";
        throw new IllegalArgumentException("Cannot convert " + elem + " to string");
    }

    private static @NotNull Number valueAsNumber(@NotNull JsonElement elem) {
        if (elem.isJsonPrimitive()) return elem.getAsNumber();
        if (elem.isJsonNull()) return 0;
        throw new IllegalArgumentException("Cannot convert " + elem + " to number");
    }

    private FeatureFlagEvaluator() {
    }

}
