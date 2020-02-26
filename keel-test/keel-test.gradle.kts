plugins {
  `java-library`
}

dependencies {
  api(project(":keel-core"))
  implementation("io.mockk:mockk")
}
