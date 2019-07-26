plugins {
  `java-library`
  id("kotlin-spring")
}

dependencies {
  api(project(":keel-core"))
  api(project(":keel-plugin"))
  api(project(":keel-veto"))

  implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core")
  implementation("org.springframework:spring-tx")

  testImplementation("org.springframework.boot:spring-boot-test-autoconfigure")
  testImplementation("dev.minutest:minutest")
  testImplementation("io.strikt:strikt-core")
}
