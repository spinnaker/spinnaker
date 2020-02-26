plugins {
  `java-library`
  id("kotlin-spring")
}

dependencies {
  implementation(project(":keel-core"))
  implementation(project(":keel-igor"))
  implementation("org.springframework:spring-context")
  implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core")
  implementation("net.swiftzer.semver:semver:1.1.0")

  implementation(project(":keel-test"))
  testImplementation("dev.minutest:minutest")
  testImplementation("io.strikt:strikt-core")
}
