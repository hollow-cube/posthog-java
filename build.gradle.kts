plugins {
    `java-library`

    `maven-publish`
    signing
    alias(libs.plugins.nmcp.aggregation)
}

group = "dev.hollowcube"
version = System.getenv("TAG_VERSION") ?: "dev"
description = "Unofficial PostHog Java Client"

repositories {
    mavenCentral()
}

dependencies {
    compileOnlyApi(libs.annotations)
    compileOnly(libs.slf4j.api)
    api(libs.gson)

    testImplementation(libs.junit)
    testImplementation(libs.slf4j.simple)
}

java {
    withSourcesJar()
    withJavadocJar()

    toolchain.languageVersion = JavaLanguageVersion.of(21)
}

tasks.test {
    useJUnitPlatform()
}

nmcpAggregation {
    centralPortal {
        username = System.getenv("SONATYPE_USERNAME")
        password = System.getenv("SONATYPE_PASSWORD")
        publishingType = "AUTOMATIC"
    }

    // Its fine we dont use multiple projects
    publishAllProjectsProbablyBreakingProjectIsolation()
}

publishing.publications.create<MavenPublication>("maven") {
    groupId = "dev.hollowcube"
    artifactId = "posthog-java"
    version = project.version.toString()

    from(project.components["java"])

    pom {
        name.set(artifactId)
        description.set(project.description)
        url.set("https://github.com/hollow-cube/posthog-java")

        licenses {
            license {
                name.set("MIT")
                url.set("https://github.com/hollow-cube/posthog-java/blob/main/LICENSE")
            }
        }

        developers {
            developer {
                id.set("mworzala")
                name.set("Matt Worzala")
                email.set("matt@hollowcube.dev")
            }
        }

        issueManagement {
            system.set("GitHub")
            url.set("https://github.com/hollow-cube/posthog-java/issues")
        }

        scm {
            connection.set("scm:git:git://github.com/hollow-cube/posthog-java.git")
            developerConnection.set("scm:git:git@github.com:hollow-cube/posthog-java.git")
            url.set("https://github.com/hollow-cube/posthog-java")
            tag.set(System.getenv("TAG_VERSION") ?: "HEAD")
        }

        ciManagement {
            system.set("Github Actions")
            url.set("https://github.com/hollow-cube/posthog-java/actions")
        }
    }
}

signing {
    isRequired = System.getenv("CI") != null

    val privateKey = System.getenv("GPG_PRIVATE_KEY")
    val keyPassphrase = System.getenv()["GPG_PASSPHRASE"]
    useInMemoryPgpKeys(privateKey, keyPassphrase)

    sign(publishing.publications)
}
