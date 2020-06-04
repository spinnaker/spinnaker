plugins {
  `java-library`
}

dependencies {
  api(project(":keel-core"))
  implementation("io.mockk:mockk")
  implementation(project(":keel-spring-test-support"))
}
