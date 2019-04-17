plugins {
  `java-library`
  id("kotlin-spring")
}

dependencies {
  implementation(project(":keel-retrofit"))
  implementation(project(":keel-plugin"))
  implementation(project(":keel-front50"))

  implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core")
  implementation("org.springframework:spring-context")
  implementation("org.springframework.boot:spring-boot-autoconfigure")
  testImplementation("io.strikt:strikt-core")
  testImplementation("dev.minutest:minutest")
}
