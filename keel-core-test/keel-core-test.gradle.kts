plugins {
  `java-library`
}

dependencies {
  implementation(project(":keel-api"))
  implementation(project(":keel-core"))
  implementation(project(":keel-test"))
  implementation("org.junit.jupiter:junit-jupiter-api")
  implementation("org.junit.jupiter:junit-jupiter-params")
  implementation("io.strikt:strikt-core")
  implementation("io.mockk:mockk")
  api("dev.minutest:minutest")
}
