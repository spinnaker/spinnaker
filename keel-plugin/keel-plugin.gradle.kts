plugins {
  `java-library`
  id("kotlin-spring")
}

dependencies {
  api(project(":keel-core"))
  implementation(project(":keel-orca"))

  testImplementation("dev.minutest:minutest")
  testImplementation("io.strikt:strikt-core")
}
