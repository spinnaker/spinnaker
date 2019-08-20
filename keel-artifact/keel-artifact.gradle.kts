plugins {
  `java-library`
  id("kotlin-spring")
}

dependencies {
  implementation(project(":keel-core"))
  implementation(project(":keel-actuator"))
  implementation(project(":keel-igor"))
  implementation("org.springframework:spring-context")
  implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core")

  testImplementation("dev.minutest:minutest")
  testImplementation("io.strikt:strikt-core")
}
