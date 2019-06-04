plugins {
  `java-library`
  id("kotlin-spring")
}

dependencies {
  api(project(":keel-core"))
  api(project(":keel-plugin"))

  implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core")

  testImplementation("org.springframework.boot:spring-boot-test-autoconfigure")
  testImplementation("dev.minutest:minutest")
  testImplementation("io.strikt:strikt-core")
}
