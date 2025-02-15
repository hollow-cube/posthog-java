# PostHog Java Client

[![license](https://img.shields.io/github/license/hollow-cube/posthog-java.svg)](LICENSE)

## Features

* [Events/person profiles](#event-capturing)
* [Feature flags](#feature-flags) (including [local evaluation](#local-evaluation))
* [Error tracking (beta)](#error-tracking-beta)

### Why?

There is an official Java client for PostHog ([here](https://github.com/PostHog/posthog-java)), however it unfortunately
has some shortcomings:

* No support for feature flag local evalution
    * There is a PR for this [here](https://github.com/PostHog/posthog-java/pull/54#issuecomment-2176424517),
      however it is not a priority for PostHog and has been left since June 2024.
* [Shading all of its dependencies](https://github.com/PostHog/posthog-java/issues/51), this notably includes
* okhttp3, which brings along the entire Kotlin stdlib (a second copy for those already using Kotlin).
* Various other strange implementation details, such as duplicating the exact same network request to test a feature
  flag
  just to check for
  null ([link](https://github.com/PostHog/posthog-java/blob/95928d9163ec52b24815b69ffabd710e9b7b1331/posthog/src/main/java/com/posthog/java/PostHog.java#L221)).
* No support for new features (such as error tracking).

There has been some [discussion/planning](https://github.com/PostHog/posthog-android/pull/129#issuecomment-2145379090)
around extracting the relevant parts of the Android SDK to act as a standalone Java SDK, however this has yet to
materialize.

## Install

`posthog-java` is available
on [maven central](https://search.maven.org/search?q=g:dev.hollowcube%20AND%20a:posthog-java).

```groovy
repositories {
    mavenCentral()
}

dependencies {
    implementation 'dev.hollowcube:posthog-java:<see releases>'
}
```

You will need to initialize the client with your PostHog project API key.

```java
import net.hollowcube.posthog.PostHog;

class Sample {
    private static final String POSTHOG_API_KEY = "phc_mK0jji1aC3hvMBGLOLjuVARqolDGPS9AiuNUOhMwVyA";
    private static final String POSTHOG_HOST = "https://us.i.posthog.com";

    public static void main(String[] args) {
        PostHog.init("phc_<your project api key>", config -> config.endpoint(POSTHOG_HOST));

        // Run application

        PostHog.shutdown(); // Send remaining events in queue
    }
}
```

<details>
  <summary>Usage with non-static client</summary>

It is also possible to use the client in a non-static/singleton pattern.

```java
import net.hollowcube.posthog.PostHog;

class Sample {
    private static final String POSTHOG_API_KEY = "phc_mK0jji1aC3hvMBGLOLjuVARqolDGPS9AiuNUOhMwVyA";
    private static final String POSTHOG_HOST = "https://us.i.posthog.com";

    public static void main(String[] args) {
        PostHogClient posthog = PostHogClient.newBuilder(POSTHOG_API_KEY)
                .endpoint(POSTHOG_HOST)
                .build();

        // Run application

        posthog.shutdown(Duration.ofSeconds(10));
    }
}
```

</details>

## Event Capturing

You can send custom events using `capture`:

```java
PostHog.capture("distinct_id_of_user","user_signed_up");
```

> [!TIP]
> PostHog recommends using an `[object] [verb]` format for your event names,
> where `[object]` is the entity that the behavior relates to, and `[verb]`
> is the behavior itself. For example, `project created`, `user signed up`,
> or `invite sent`.

### Setting Event Properties

Optionally, you can also include additional information in the event by setting the properties value:

```java
public static void handleUserSignup() {
    // Logic...

    PostHog.capture("distinct_id_of_the_user", "user_signed_up", Map.of(
            "login_type", "email",
            "is_free_trial", true
    ));
}
```

Any `Gson` serializable (to object) type may be used as event properties.

<details>
  <summary>Event properties from struct</summary>

```java
public static void handleUserSignup() {
    // Logic...

    record UserSignedUpEvent(String loginType, boolean isFreeTrial) {
    }

    PostHog.capture("distinct_id_of_the_user", "user_signed_up", new UserSignedUpEvent("email", true));
}
```

</details>

### Person Properties

By default, captured events will be associated with the `distinct_id` to create a
[person profile](https://posthog.com/docs/data/persons). It is possible to set
[person properties](https://posthog.com/docs/data/user-properties) in these events
using `$set` or `$set_once` (
see [here](https://posthog.com/docs/data/user-properties#what-is-the-difference-between-set-and-set_once)
for more details).

```java
public void sample() {
    PostHog.capture("distinct_id", "event_name", Map.of(
            "$set", Map.of("name", "Max Hedgehog"),
            "$set_once", Map.of("initial_url", "/blog")
    ));
}
```

Defining person properties without an associated event cal also be
done using `identify`, the following is equivalent.

```java
public void sample() {
    PostHog.identify("distinct_id",
            Map.of("name", "Max Hedgehog"), // $set
            Map.of("initial_url", "/blog") // $set_once
    );
}
```

To capture an [anonymous event](https://posthog.com/docs/data/anonymous-vs-identified-events),
set `$process_person_profile` to `false`.

```java
public void sample() {
    PostHog.capture("distinct_id", "event_name", Map.of(
            "$process_person_profile", false
    ));
}
```

### Alias

Sometimes, you want to assign multiple distinct IDs to a single user. This is helpful when
your primary distinct ID is inaccessible. For example, if a distinct ID used on the frontend
is not available in your backend.

In this case, you can use alias to assign another distinct ID to the same user.

```java
public void sample() {
    PostHog.alias("distinct_id", "new_distinct_id");
}
```

See the [alias docs](https://posthog.com/docs/data/identify#alias-assigning-multiple-distinct-ids-to-the-same-user)
for more information.

### Group Analytics

Group analytics allows you to associate an event with a group (e.g. teams, organizations, etc.). Read the
[Group Analytics](https://posthog.com/docs/user-guides/group-analytics) guide for more information.

Events can be associated with a group by providing the `$group` property, for example:

```java
public void sample() {
    PostHog.capture("distinct_id_of_user", "event_name", Map.of(
            "$groups", Map.of("company", "company_id_in_your_db")
    ));
}
```

You can update properties for a group using `groupIdentify`.

```java
public void sample() {
    PostHog.groupIdentify("company", "company_id_in_your_db", Map.of(
            "name", "Hollow Cube",
            "github_url", "https://github.com/hollow-cube"
    ));
}
```

The `name` property on a group is used as a display name in the PostHog UI. If not present, the ID will be used instead.

## Feature Flags

[Feature flags](https://posthog.com/docs/feature-flags) allow you to enable or disable features dynamically at runtime.

#### Boolean feature flags

```java
public void sample() {
    boolean isMyFlagEnabled = PostHog.isFeatureEnabled("my-flag", "distinct_id_of_user");
    if (isMyFlagEnabled) {
        // Special logic
    }
}
```

#### Multivariate feature flags

```java
public void sample() {
    FeatureFlagState myFlag = PostHog.getFeatureFlag("my_flag", "distinct_id_of_user");
    if ("variant-key".equals(myFlag.getVariant())) { // replace 'variant-key' with the key of your variant
        // Do something special
    }
}
```

#### Fetching all flags for a user

```java
public void sample() {
    FeatureFlagStates features = PostHog.getAllFeatureFlags("distinct_id_of_user");
    if (features.isEnabled("my_flag")) {
        // Special logic
    }
    if ("variant-key".equals(features.getVariant("my_flag"))) {
        // Do something special
    }
}
```

#### Overriding server properties

Sometimes, you may want to evaluate feature flags using person properties, groups, or group properties that haven't
been ingested yet, or were set incorrectly earlier (or you want to evaluate locally, see below).

You can provide properties to evaluate the flag with by setting the `person properties`, `groups`, and
`group properties` options on the `FeatureFlagContext`. PostHog will then use these values to evaluate the flag, instead
of any properties currently stored on your PostHog server.

```java
public void sample() {
    FeatureFlagState result = PostHog.getFeatureFlag("my_flag", "distinct_id", FeatureFlagContext.newBuilder()
            .personProperties(Map.of("name", "Max Hedgehog"))
            .groups(Map.of("your_group_type", "your_group_id"))
            .groupProperties(Map.of("your_group_type", Map.of(
                    "group_property_name", "value"
            )))
            .build());
    if (result.isEnabled()) {
        // Special logic...
    }
}
```

#### Sending `$feature_flag_called` events

Capturing $feature_flag_called events enable PostHog to know when a flag was accessed by a user and thus provide
analytics and insights on the flag. These are never sent by default, but can be enabled by setting
`sendFeatureFlagEvents` on the client or the specific `FeatureFlagContext`.

```java
public void sample() {
    PostHog.getFeatureFlag("my_flag", "distinct_id", new FeatureFlagContext.Builder()
            .sendFeatureFlagEvents(true)
            .build());
}
```

### Local Evaluation

Evaluating feature flags requires making a request to PostHog for each flag. However, you can improve
performance by evaluating flags locally. Instead of making a request for each flag, we will
periodically request and store feature flag definitions locally, enabling you to evaluate flags
without making additional requests.

It is best practice to use local evaluation flags when possible, since this enables you to resolve
flags faster and with fewer API calls.

There are 3 steps to enable local evaluation:

#### 1. Create a personal API key ([link](https://us.posthog.com/settings/user-api-keys)).

#### 2. Initialize client with your personal API key

When you initialize PostHog with your personal API key, PostHog will use your the key to
automatically fetch feature flag definitions. These definitions are then used to evaluate
feature flags locally.

By default, PostHog fetches these definitions every 5 minutes. However, you can change
this frequency by specifying a different value in the polling interval argument in the
client config.

> [!NOTE]
> For billing purposes, PostHog counts the request to fetch the feature flag definitions
> as being equivalent to 10 decide requests.

```java
public static void main(String[] args) {
    PostHog.init("<your project api key>", config -> config
            .endpoint("https://us.i.posthog.com")
            .personalApiKey("<your personal api key>"));
}
```

#### 3. Evaluate your feature flag

To evaluate the feature flag, call any of the flag related methods, like `getFeatureFlag`
or `getAllFlags`, as you normally would. The only difference is that you must provide any
`person properties`, `groups` or `group properties` used to evaluate the release conditions
of the flag.

```java
public void sample() {
    FeatureFlagState result = PostHog.getFeatureFlag("my_flag", "distinct_id", FeatureFlagContext.newBuilder()
            .personProperties(Map.of("name", "Max Hedgehog"))
            .groups(Map.of("your_group_type", "your_group_id"))
            .groupProperties(Map.of("your_group_type", Map.of(
                    "group_property_name", "value"
            )))
            .build());
    if (result.isEnabled()) {
        // Special logic...
    }
}
```

If the client is unable to evaluate the flag locally, it will make a request to the server
to fetch the value. This behavior can be disabled by setting `allowRemoteFeatureFlagEvaluation`
to `false` either on the client or the specific `FeatureFlagContext`.

```java
public void sample() {
    FeatureFlagState result = PostHog.getFeatureFlag("my_flag", "distinct_id", new FeatureFlagContext.Builder()
            .setAllowRemoteEvaluation(false)
            .build());
    if (result.isEnabled()) {
        // Special logic...
    }
}
```

A flag which is only evaluated locally may create an indeterminate result if we do not have all required
context to evaluate the flag. Indeterminate results are treated as disabled, but can be checked explicitly
using `FeatureFlagState#isIndeterminate`.

It is not possible to evaluate flags that:

* Have experience continuity enabled, which is set when you check
  ['persist flag across authentication steps'](https://posthog.com/docs/feature-flags/creating-feature-flags#persisting-feature-flags-across-authentication-steps)
  on your feature flag.
* Are linked to an [early access feature](https://posthog.com/docs/feature-flags/early-access-feature-management).
* Depend on [static cohorts](https://posthog.com/docs/data/cohorts#static-cohorts).
* Use `is_not_set` as an evaluation condition.
* Contain a condition that uses a property which was not passed as context.

#### Reloading flags

The client periodically refreshes feature flag definitions, however you can also trigger an immediate
refresh using `PostHog#reloadFeatureFlags`.

## Error Tracking (beta)

> [!WARNING]
> Error tracking is currently in beta and may change significantly in the future. It must be enabled
> from within your PostHog account for use. See [here](https://posthog.com/docs/error-tracking) for
> official docs.

Error tracking enables tracking, investigating, and resolving exceptions in your app. Exceptions can be captured
using `PostHog#captureException`.

```java
public void sample() {
    try {
        int a = 1 / 0; // Some bad logic...
    } catch (Exception e) {
        PostHog.captureException(e);

        // A distinct id may also be provided to associate the event with a user
        PostHog.captureException(e, "distinct_id");

        // Extra properties may also be provided with the event
        PostHog.captureException(e, "distinct_id", Map.of("key", "value"));
    }
}
```

`posthog-java` does not have any automatic exception capturing, all exceptions must be reported to the client
manually. It is recommended to set up a global uncaught exception handler to capture any missed exceptions.

```java
public static void main(String[] args) {
    Thread.setDefaultUncaughtExceptionHandler((ignored, e) -> {
        PostHog.captureException(e);
    });
}
```

## Contributing

Contributions via PRs and issues are always welcome.

## License

This project is licensed under the [MIT License](LICENSE).

README docs loosely based on the official [Java](https://posthog.com/docs/libraries/java)
and [Go](https://posthog.com/docs/libraries/go) Docs.
