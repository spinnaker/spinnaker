plugins {
  `java-library`
  id("kotlin-spring")
}

dependencies {
  implementation(project(":keel-retrofit"))
  implementation(project(":keel-core"))
  implementation(project(":keel-front50"))
  implementation("org.springframework.boot:spring-boot-autoconfigure")

  implementation ("io.github.resilience4j:resilience4j-kotlin")

  testImplementation("dev.minutest:minutest")
  testImplementation("io.strikt:strikt-core")
  testImplementation(project(":keel-spring-test-support"))
  testImplementation(project(":keel-test"))
}
