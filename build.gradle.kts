plugins {
    `java-library`
}

group = "net.hollowcube"
version = "0.1.0"

repositories {
    mavenCentral()
}

dependencies {
    compileOnlyApi("org.jetbrains:annotations:26.0.2")
    compileOnly("org.slf4j:slf4j-api:2.0.16")

    api("com.google.code.gson:gson:2.12.1")

    testImplementation(platform("org.junit:junit-bom:5.10.0"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("org.slf4j:slf4j-simple:2.0.16")
}

tasks.test {
    useJUnitPlatform()
}
