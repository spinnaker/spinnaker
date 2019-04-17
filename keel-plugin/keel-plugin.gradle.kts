plugins {
  `java-library`
  id("kotlin-spring")
}

dependencies {
  api(project(":keel-core"))

  implementation("org.springframework:spring-context")
  implementation("org.springframework.boot:spring-boot-autoconfigure")
  implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core")
  implementation("com.netflix.spinnaker.kork:kork-core")

  testImplementation(project(":keel-spring-test-support"))
  testImplementation("org.springframework.boot:spring-boot-starter-web")
  testImplementation("dev.minutest:minutest")
  testImplementation("io.strikt:strikt-core")
}
