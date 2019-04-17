plugins {
  `java-library`
  id("kotlin-spring")
}

dependencies {
  implementation(project(":keel-core"))
  implementation("com.netflix.spinnaker.kork:kork-core")
  implementation("org.springframework:spring-context")
  implementation("org.springframework.boot:spring-boot-autoconfigure")
}
