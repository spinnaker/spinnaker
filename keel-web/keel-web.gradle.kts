plugins {
  `java-library`
  id("kotlin-spring")
  application
}

apply(plugin = "io.spinnaker.package")

dependencies {
  api(project(":keel-core"))
  api(project(":keel-clouddriver"))
  api(project(":keel-artifact"))
  api(project(":keel-sql"))
  api(project(":keel-docker"))
  api(project(":keel-echo"))
  api(project(":keel-igor"))

  implementation(project(":keel-bakery-plugin"))
  implementation(project(":keel-ec2-plugin"))
  implementation(project(":keel-titus-plugin"))

  implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core")
  implementation("com.netflix.spinnaker.kork:kork-web")
  implementation("com.netflix.spinnaker.kork:kork-artifacts")
  implementation("org.springframework.boot:spring-boot-starter-actuator")
  implementation("org.springframework.boot:spring-boot-starter-web")
  implementation("org.springframework.security:spring-security-config")
  implementation("com.netflix.spinnaker.fiat:fiat-api:${property("fiatVersion")}")
  implementation("com.netflix.spinnaker.fiat:fiat-core:${property("fiatVersion")}")
  implementation("net.logstash.logback:logstash-logback-encoder")
  implementation("org.springdoc:springdoc-openapi-webmvc-core:1.4.3")
  implementation("org.springdoc:springdoc-openapi-kotlin:1.4.3")
  implementation("org.apache.maven:maven-artifact:3.6.3")
  implementation("com.netflix.spinnaker.kork:kork-plugins")

  runtimeOnly("com.netflix.spinnaker.kork:kork-runtime") {
    // these dependencies weren't previously being included, keeping them out for now, if there
    // is a need for them in the future these excludes are easy enough to delete...
    exclude(mapOf("group" to "com.netflix.spinnaker.kork", "module" to "kork-swagger"))
    exclude(mapOf("group" to "com.netflix.spinnaker.kork", "module" to "kork-stackdriver"))
    exclude(mapOf("group" to "com.netflix.spinnaker.kork", "module" to "kork-secrets-aws"))
    exclude(mapOf("group" to "com.netflix.spinnaker.kork", "module" to "kork-secrets-gcp"))
  }

  testImplementation("io.strikt:strikt-jackson")
  testImplementation(project(":keel-test"))
  testImplementation(project(":keel-core-test"))
  testImplementation(project(":keel-spring-test-support"))
  testImplementation(project(":keel-retrofit"))
  testImplementation(project(":keel-clouddriver"))
  testImplementation("com.netflix.spinnaker.kork:kork-security")
  testImplementation("com.squareup.okhttp3:mockwebserver")
  testImplementation("org.testcontainers:mysql")
  testImplementation("org.openapi4j:openapi-schema-validator:0.7")
  testImplementation("com.netflix.spinnaker.kork:kork-plugins")
}

application {
  mainClassName = "com.netflix.spinnaker.keel.MainKt"
}
