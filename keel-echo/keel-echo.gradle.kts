plugins {
  `java-library`
  id("kotlin-spring")
}

dependencies {
  implementation(project(":keel-retrofit"))
  implementation(project(":keel-core"))
  implementation("org.springframework.boot:spring-boot-autoconfigure")

  testImplementation("dev.minutest:minutest")
  testImplementation("io.strikt:strikt-core")
  testImplementation(project(":keel-test"))
}
