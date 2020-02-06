plugins {
  `java-library`
  id("kotlin-spring")
}

dependencies {
  implementation(project(":keel-retrofit"))
  implementation(project(":keel-core"))
  implementation("com.netflix.spinnaker.kork:kork-artifacts")
  implementation("org.springframework.boot:spring-boot-autoconfigure")
  implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core")

  testImplementation("dev.minutest:minutest")
  testImplementation("io.strikt:strikt-core")
  testImplementation(project(":keel-test"))
}
