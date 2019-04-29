plugins {
  `java-library`
  id("kotlin-spring")
}

dependencies {
  implementation(project(":keel-retrofit"))
  implementation("com.netflix.archaius:archaius-core")
  implementation("org.springframework.boot:spring-boot-autoconfigure")
  implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core")

  testImplementation("dev.minutest:minutest")
  testImplementation("io.strikt:strikt-core")
  testImplementation(project(":keel-spring-test-support"))
}
