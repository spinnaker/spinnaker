plugins {
  `java-library`
  id("kotlin-spring")
}

dependencies {
  implementation(project(":keel-retrofit"))
  implementation(project(":keel-core"))
  implementation("org.springframework.boot:spring-boot-autoconfigure")

  implementation("com.slack.api:slack-api-model-kotlin-extension:1.4.1")
  implementation("com.slack.api:slack-api-client-kotlin-extension:1.4.1")

  testImplementation("dev.minutest:minutest")
  testImplementation("io.strikt:strikt-core")
  testImplementation(project(":keel-test"))
  testImplementation(project(":keel-core-test"))
}
