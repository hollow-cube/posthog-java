package net.hollowcube.posthog;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.annotations.SerializedName;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;

record FeatureFlagsResponse(
        @NotNull List<Flag> flags,
        @SerializedName("group_type_mapping")
        @Nullable Map<String, String> groupTypeMapping,
        @NotNull Map<String, JsonObject> cohorts
) {

    record Flag(
            @NotNull String key,
            @SerializedName("is_simple_flag")
            boolean isSimpleFlag,
            @SerializedName("rollout_percentage")
            @Nullable Integer rolloutPercentage,
            boolean active,
            @NotNull Filters filters,
            @SerializedName("ensure_experience_continuity")
            @Nullable Boolean ensureExperienceContinuity
    ) {
    }

    record Filters(
            @SerializedName("aggregation_group_type_index")
            @Nullable Integer aggregationGroupTypeIndex,
            @NotNull List<Condition> groups,
            @Nullable Variants multivariate,
            @NotNull Map<String, String> payloads
    ) {
    }

    record Variants(
            @Nullable List<Variant> variants
    ) {
    }

    record Variant(
            @NotNull String key,
            @NotNull String name,
            @SerializedName("rollout_percentage")
            @Nullable Integer rolloutPercentage
    ) {
    }

    record Condition(
            @Nullable List<Property> properties,
            @SerializedName("rollout_percentage")
            @Nullable Integer rolloutPercentage,
            @Nullable String variant
    ) {
    }

    record Property(
            @NotNull String key,
            @NotNull String operator,
            @NotNull JsonElement value,
            @NotNull String type,
            boolean negation
    ) {
    }


}
