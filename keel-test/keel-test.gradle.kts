plugins {
  `java-library`
}

dependencies {
  api(project(":keel-api"))
  api(project(":keel-core"))
  api(project(":keel-artifact"))
  implementation("io.mockk:mockk")
  implementation(project(":keel-spring-test-support"))
}
