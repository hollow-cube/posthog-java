package net.hollowcube.posthog;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Names with special functionality in PostHog.
 */
public final class PostHogNames {
    public static final String IDENTIFY = "$identify";
    public static final String CREATE_ALIAS = "$create_alias";
    public static final String SET = "$set";
    public static final String SET_ONCE = "$set_once";
    public static final String UNSET = "$unset";
    public static final String PROCESS_PERSON_PROFILE = "$process_person_profile";

    public static final String GROUP = "$group";
    public static final String GROUPS = "$groups";
    public static final String GROUP_IDENTIFY = "$groupidentify";
    public static final String GROUP_TYPE = "$group_type";
    public static final String GROUP_KEY = "$groupkey";
    public static final String GROUP_SET = "$group_set";

    public static final String LIB = "$lib";
    public static final String LIB_VERSION = "$lib_version";

    // This group is typically autocaptured by other SDKs, see here
    // https://posthog.com/docs/product-analytics/autocapture
    public static final String AUTO_CAPTURE = "$autocapture";
    public static final String PAGE_VIEW = "$pageview";
    public static final String PAGE_LEAVE = "$pageleave";
    public static final String RAGE_CLICK = "$rageclick";
    public static final String SCREEN = "$screen";

    // Feature flags
    public static final String FEATURE_FLAG_CALLED = "$feature_flag_called";
    public static final String FEATURE_FLAG = "$feature_flag";
    public static final String FEATURE_FLAG_RESPONSE = "$feature_flag_response";
    public static final String FEATURE_FLAG_ERRORED = "$feature_flag_errored";

    // Exceptions
    public static final String EXCEPTION = "$exception";
    public static final String EXCEPTION_TYPE = "$exception_type";
    public static final String EXCEPTION_MESSAGE = "$exception_message";
    public static final String EXCEPTION_LIST = "$exception_list";
    public static final String EXCEPTION_PERSON_URL = "$exception_personURL";

    static @NotNull String nonNullNonEmpty(@NotNull String name, @Nullable String value) {
        if (value == null || value.isEmpty())
            throw new IllegalArgumentException(name + " may not be null or empty");
        return value;
    }

    private PostHogNames() {
    }
}
