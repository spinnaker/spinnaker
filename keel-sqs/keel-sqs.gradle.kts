plugins {
  `java-library`
  id("kotlin-spring")
}

dependencies {
  implementation(project(":keel-plugin"))
  implementation("com.amazonaws:aws-java-sdk-sqs")
  implementation("com.netflix.spinnaker.kork:kork-aws")
  implementation("org.springframework.boot:spring-boot")
  implementation("org.springframework.boot:spring-boot-autoconfigure")
  implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core")

  testImplementation("org.springframework.boot:spring-boot-starter-test")
  testImplementation("dev.minutest:minutest")
  testImplementation("io.strikt:strikt-core")
}
