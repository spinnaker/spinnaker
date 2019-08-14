plugins {
  `java-library`
  id("kotlin-spring")
}

dependencies {
  api(project(":keel-core"))
  api(project(":keel-plugin"))
  api(project(":keel-veto"))

  implementation("com.netflix.spectator:spectator-api")
  implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core")
  implementation("org.springframework:spring-tx")

  testImplementation(project(":keel-test"))
  testImplementation("org.springframework.boot:spring-boot-test-autoconfigure")
  testImplementation("dev.minutest:minutest")
  testImplementation("io.strikt:strikt-core")
}
