plugins {
  `java-library`
}

dependencies {
  implementation(project(":keel-plugin"))
  implementation("com.netflix.spinnaker.kork:kork-core")

  testImplementation("io.strikt:strikt-core")
  testImplementation("dev.minutest:minutest")
}

