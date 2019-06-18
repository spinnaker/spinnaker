plugins {
  `java-library`
  id("kotlin-spring")
}

dependencies {
  api(project(":keel-core"))

  testImplementation("dev.minutest:minutest")
  testImplementation("io.strikt:strikt-core")
}
