plugins {
    java
    id("org.springframework.boot") version "4.1.0"
    id("io.spring.dependency-management") version "1.1.7"
}

group = "com.gtublog"
version = "0.1.0-SNAPSHOT"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}

repositories {
    mavenCentral()
}

dependencyLocking {
    lockAllConfigurations()
}

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-oauth2-resource-server")
    implementation("org.springframework.boot:spring-boot-starter-quartz")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-webmvc")
    implementation("org.flywaydb:flyway-core")
    runtimeOnly("org.flywaydb:flyway-mysql")
    runtimeOnly("com.mysql:mysql-connector-j")
    implementation("io.micrometer:micrometer-registry-prometheus")

    // Spring Boot does not manage these source-collection libraries.
    implementation("org.jsoup:jsoup:1.21.1")
    implementation("com.rometools:rome:2.1.0")
    implementation("io.github.resilience4j:resilience4j-retry:2.3.0")
    implementation("io.github.resilience4j:resilience4j-ratelimiter:2.3.0")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.boot:spring-boot-testcontainers")
    testImplementation("org.springframework.security:spring-security-test")
    testImplementation("org.testcontainers:testcontainers-junit-jupiter")
    testImplementation("org.testcontainers:testcontainers-mysql")
    testImplementation("org.springframework:spring-jdbc")
    testImplementation("org.wiremock:wiremock-standalone:3.13.1")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

springBoot {
    buildInfo()
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}

tasks.test {
    useJUnitPlatform {
        excludeTags("docker")
    }
}

val mysqlCompatibilityTest by tasks.registering(Test::class) {
    description = "Runs the release-blocking MySQL 8.4 and Flyway compatibility probe."
    group = LifecycleBasePlugin.VERIFICATION_GROUP
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    useJUnitPlatform {
        includeTags("docker")
    }
    shouldRunAfter(tasks.test)
}

tasks.check {
    dependsOn(mysqlCompatibilityTest)
}
