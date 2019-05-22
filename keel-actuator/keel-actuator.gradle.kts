plugins {
  `java-library`
  id("kotlin-spring")
}

dependencies {
  api(project(":keel-core"))
  api(project(":keel-plugin"))

  testImplementation("org.springframework.boot:spring-boot-test-autoconfigure")
  testImplementation("dev.minutest:minutest")
  testImplementation("io.strikt:strikt-core")
}
