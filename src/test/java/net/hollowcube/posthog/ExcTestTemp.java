package net.hollowcube.posthog;

public class ExcTestTemp {

    private static void theBadMethod() {
        int i = 1 / 0;
    }

    private static void someOtherMethod() {
        theBadMethod();
    }

    public static void main(String[] args) {
        PostHog.init("phc_mK0jji1aC3hvMBGLOLjuVARqolDGPS9AiuNUOhMwVyA",
                config -> config.endpoint("https://us.i.posthog.com"));

        try {
            someOtherMethod();
        } catch (Exception e) {
            PostHog.captureException(e);
        }
        PostHog.shutdown();
    }
}
