plugins {
  `java-library`
  id("kotlin-spring")
}

dependencies {
  implementation(project(":keel-core"))
  implementation("org.springframework:spring-context")

  testImplementation("dev.minutest:minutest")
  testImplementation("io.strikt:strikt-core")
}
