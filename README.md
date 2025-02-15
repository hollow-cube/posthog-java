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

TODO

## Event capturing

TODO

## Feature Flags

TODO

### Local Evaluation

TODO

## Error Tracking (beta)

TODO

## Contributing

Contributions via PRs and issues are always welcome.

## License

This project is licensed under the [MIT License](LICENSE).
