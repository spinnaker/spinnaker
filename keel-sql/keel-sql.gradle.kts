plugins {
  `java-library`
  id("kotlin-spring")
}

dependencies {
  implementation(project(":keel-core"))
  implementation("com.netflix.spinnaker.kork:kork-sql")
  implementation("org.springframework:spring-jdbc")
  implementation("org.springframework:spring-tx")
  implementation("org.jooq:jooq:3.11.11")
  implementation("com.zaxxer:HikariCP")
  implementation("org.liquibase:liquibase-core")

  runtimeOnly("mysql:mysql-connector-java")

  testImplementation("com.netflix.spinnaker.kork:kork-sql-test")
  testImplementation("io.strikt:strikt-core")
  testImplementation(project(":keel-spring-test-support"))
  testImplementation(project(":keel-core-test"))
  testImplementation(project(":keel-api"))
  testImplementation("org.testcontainers:mysql")
  testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-core")
}
