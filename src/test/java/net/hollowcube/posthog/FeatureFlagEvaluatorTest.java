package net.hollowcube.posthog;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

class FeatureFlagEvaluatorTest {

    @Test
    void failWithExperienceContinuity() {
        var raw = "{\"id\":107924,\"team_id\":72878,\"name\":\"\",\"key\":\"payload-test\",\"filters\":{\"groups\":[{\"variant\":null,\"properties\":[],\"rollout_percentage\":100}],\"payloads\":{},\"multivariate\":null},\"deleted\":false,\"active\":true,\"ensure_experience_continuity\":true}";
        var result = evalFlag(raw, "person-a", null);
        assertTrue(result.isInconclusive());
    }

    @Test
    void inactiveFlagAlwaysDisabled() {
        var raw = "{\"id\":107924,\"team_id\":72878,\"name\":\"\",\"key\":\"payload-test\",\"filters\":{\"groups\":[{\"variant\":null,\"properties\":[],\"rollout_percentage\":100}],\"payloads\":{},\"multivariate\":null},\"deleted\":false,\"active\":false,\"ensure_experience_continuity\":false}";
        var result = evalFlag(raw, "person-a", null);
        assertFalse(result.isEnabled());
    }

    @Test
    void noConditionsFullRollout() {
        var raw = "{\"id\":107924,\"team_id\":72878,\"name\":\"\",\"key\":\"payload-test\",\"filters\":{\"groups\":[{\"variant\":null,\"properties\":[],\"rollout_percentage\":100}],\"payloads\":{},\"multivariate\":null},\"deleted\":false,\"active\":true,\"ensure_experience_continuity\":false}";
        var result = evalFlag(raw, "person-a", null);
        assertTrue(result.isEnabled());
    }

    @Test
    void noConditionsWithPayload() {
        assumeFalse(true, "TODO: Implement payloads");

        var raw = "{\"id\":107924,\"team_id\":72878,\"name\":\"\",\"key\":\"payload-test\",\"filters\":{\"groups\":[{\"variant\":null,\"properties\":[],\"rollout_percentage\":100}],\"payloads\":{\"true\":\"{\\\"i am\\\": \\\"a payload yay!\\\"}\"},\"multivariate\":null},\"deleted\":false,\"active\":true,\"ensure_experience_continuity\":false}";
        var result = evalFlag(raw, "person-a", null);
        assertTrue(result.isEnabled());
    }

    @Test
    void multiVariate() {
        var raw = "{\"id\":107923,\"team_id\":72878,\"name\":\"\",\"key\":\"multivariant-test\",\"filters\":{\"groups\":[{\"variant\":null,\"properties\":[],\"rollout_percentage\":100}],\"payloads\":{\"variant-a\":\"{\\\"a\\\": \\\"has_a_payload\\\"}\"},\"multivariate\":{\"variants\":[{\"key\":\"variant-a\",\"name\":\"\",\"rollout_percentage\":50},{\"key\":\"variant-b\",\"name\":\"\",\"rollout_percentage\":50}]}},\"deleted\":false,\"active\":true,\"ensure_experience_continuity\":false}";
        var resultA = evalFlag(raw, "variant-a-user-r", null); // (-r is arbitrary to get hashed into variant a)
        assertTrue(resultA.isEnabled());
        assertEquals("variant-a", resultA.getVariant());
        var resultB = evalFlag(raw, "variant-b-user-b", null); // (-b is arbitrary to get hashed into variant b)
        assertTrue(resultB.isEnabled());
        assertEquals("variant-b", resultB.getVariant());
    }

    @Test
    void stringPropertyMatch() {
        var raw = "{\"id\":107198,\"team_id\":72878,\"name\":\"\",\"key\":\"test\",\"filters\":{\"groups\":[{\"variant\":null,\"properties\":[{\"key\":\"username\",\"type\":\"person\",\"value\":[\"person-a\",\"person-b\"],\"operator\":\"exact\"}],\"rollout_percentage\":100}],\"payloads\":{},\"multivariate\":null},\"deleted\":false,\"active\":true,\"ensure_experience_continuity\":false}";

        FeatureFlagState result;
        result = evalFlag(raw, "person-a", new FeatureFlagContext(null, Map.of("username", "person-a"), null, null, null));
        assertTrue(result.isEnabled()); // Person in group
        result = evalFlag(raw, "person-c", new FeatureFlagContext(null, Map.of("username", "person-c"), null, null, null));
        assertFalse(result.isEnabled()); // Person not in group
        result = evalFlag(raw, "person-a", new FeatureFlagContext(null, Map.of("something", "else"), null, null, null));
        assertFalse(result.isEnabled()); // No username context
        result = evalFlag(raw, "person-c", null);
        assertFalse(result.isEnabled()); // No context at all
    }

    private static final Gson GSON = new GsonBuilder().disableJdkUnsafe().create();

    private static @NotNull FeatureFlagState evalFlag(@NotNull String raw, @NotNull String distinctId, @Nullable FeatureFlagContext context) {
        var flag = GSON.fromJson(raw, FeatureFlagsResponse.Flag.class);
        return FeatureFlagEvaluator.evaluateFeatureFlag(GSON, flag, distinctId,
                Objects.requireNonNullElse(context, FeatureFlagContext.EMPTY));
    }

    @Nested
    class MatchPropertyTest {

        @Test
        void exactStringArrayPresent() {
            var property = "{\"key\":\"username\",\"type\":\"person\",\"value\":[\"person-a\",\"person-b\"],\"operator\":\"exact\"}";
            assertTrue(test(property, Map.of("username", "person-a")).isEnabled());
        }

        @Test
        void exactStringArrayMissing() {
            var property = "{\"key\":\"username\",\"type\":\"person\",\"value\":[\"person-a\",\"person-b\"],\"operator\":\"exact\"}";
            assertFalse(test(property, Map.of("username", "person-z")).isEnabled());
        }

        @Test
        void exactStringPresent() {
            var property = "{\"key\":\"username\",\"type\":\"person\",\"value\":\"person-a\",\"operator\":\"exact\"}";
            assertTrue(test(property, Map.of("username", "person-a")).isEnabled());
        }

        @Test
        void exactNumberPresent() {
            var property = "{\"key\":\"username\",\"type\":\"person\",\"value\":12,\"operator\":\"exact\"}";
            assertTrue(test(property, Map.of("username", 12)).isEnabled());
        }

        @Test
        void icontainsString() {
            var property = "{\"key\":\"username\",\"type\":\"person\",\"value\":\"AAAA\",\"operator\":\"icontains\"}";
            assertTrue(test(property, Map.of("username", "aaaaaaaaaa")).isEnabled());
        }

        @Test
        void regexString() {
            var property = "{\"key\":\"username\",\"type\":\"person\",\"value\":\"te.+st\",\"operator\":\"regex\"}";
            assertTrue(test(property, Map.of("username", "teaaast")).isEnabled());
            assertFalse(test(property, Map.of("username", "test")).isEnabled());
        }

        @Test
        void ord() {
            var property = "{\"key\":\"username\",\"type\":\"person\",\"value\":12,\"operator\":\"gt\"}";
            assertTrue(test(property, Map.of("username", 33)).isEnabled());
            assertFalse(test(property, Map.of("username", 11)).isEnabled());
        }

        @Test
        void isNotSetInconclusive() {
            var property = "{\"key\":\"username\",\"type\":\"person\",\"value\":[\"person-a\",\"person-b\"],\"operator\":\"is_not_set\"}";
            assertTrue(test(property, Map.of("username", "person-z")).isInconclusive());
        }

        private @NotNull FeatureFlagState test(@NotNull String property, @NotNull Map<String, Object> person) {
            var prop = GSON.fromJson(property, FeatureFlagsResponse.Property.class);
            return FeatureFlagEvaluator.matchProperty(prop, GSON.toJsonTree(person).getAsJsonObject());
        }
    }

}
