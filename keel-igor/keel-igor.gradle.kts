plugins {
  `java-library`
  id("kotlin-spring")
}

dependencies {
  implementation(project(":keel-retrofit"))
  implementation(project(":keel-core"))
  implementation("org.springframework.boot:spring-boot-autoconfigure")
  implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core")

  implementation ("io.github.resilience4j:resilience4j-kotlin")

  testImplementation("dev.minutest:minutest")
  testImplementation("io.strikt:strikt-core")
  testImplementation(project(":keel-spring-test-support"))
  testImplementation(project(":keel-test"))
}
