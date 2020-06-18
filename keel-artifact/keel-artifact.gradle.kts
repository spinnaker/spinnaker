plugins {
  `java-library`
  id("kotlin-spring")
}

dependencies {
  implementation(project(":keel-core"))
  implementation(project(":keel-igor"))
  implementation(project(":keel-clouddriver"))
  implementation("org.springframework:spring-context")
  implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core")
  implementation("com.netflix.spinnaker.kork:kork-artifacts")
  testImplementation("dev.minutest:minutest")
  testImplementation("io.strikt:strikt-core")
  testImplementation(project(":keel-test"))
}
