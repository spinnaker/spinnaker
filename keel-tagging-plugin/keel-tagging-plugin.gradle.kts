plugins {
  `java-library`
  id("kotlin-spring")
}

dependencies {
  implementation(project(":keel-actuator"))
  implementation(project(":keel-core"))
  implementation(project(":keel-plugin"))
  implementation(project(":keel-clouddriver"))
  implementation(project(":keel-orca"))
  implementation("org.springframework:spring-context")
  implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core")
  implementation("com.netflix.spinnaker.kork:kork-security")

  testImplementation("dev.minutest:minutest")
  testImplementation("io.strikt:strikt-core")
  testImplementation(project (":keel-core-test"))
}
