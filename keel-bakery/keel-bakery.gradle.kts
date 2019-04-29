plugins {
  `java-library`
  id("kotlin-spring")
}

dependencies {
  implementation(project(":keel-core"))
  implementation(project(":keel-plugin"))
  implementation(project(":keel-clouddriver"))
  implementation(project(":keel-igor"))
  implementation(project(":keel-mahe"))
  implementation(project(":keel-orca"))
  implementation("org.springframework:spring-context")
  implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core")

  testImplementation("dev.minutest:minutest")
  testImplementation("io.strikt:strikt-core")
  testImplementation(project(":keel-spring-test-support"))
}
