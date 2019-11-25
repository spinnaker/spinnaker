plugins {
  `java-library`
  id("kotlin-spring")
}

dependencies {
  api(project(":keel-core"))
  implementation("org.springframework.boot:spring-boot-starter-actuator")
  implementation("org.springframework.boot:spring-boot-starter-web")

  testImplementation(project(":keel-plugin"))
  testImplementation(project(":keel-bakery-plugin"))
  testImplementation("dev.minutest:minutest")
  testImplementation("io.strikt:strikt-core")
}
