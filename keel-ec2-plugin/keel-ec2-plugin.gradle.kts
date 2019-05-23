plugins {
  `java-library`
  id("kotlin-spring")
}

dependencies {
  api(project(":keel-plugin"))
  implementation(project(":keel-clouddriver"))
  implementation(project(":keel-orca"))
  implementation(project(":keel-retrofit"))
  implementation("com.netflix.spinnaker.kork:kork-core")
  implementation("com.netflix.spinnaker.kork:kork-web")
  implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core")
  implementation("org.springframework:spring-context")
  implementation("org.springframework.boot:spring-boot-autoconfigure")

  testImplementation("io.strikt:strikt-jackson")
  testImplementation("dev.minutest:minutest")
  testImplementation("com.fasterxml.jackson.dataformat:jackson-dataformat-yaml")
  testImplementation("org.funktionale:funktionale-partials")
}
