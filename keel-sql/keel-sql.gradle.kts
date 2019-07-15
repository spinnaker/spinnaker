import com.diffplug.gradle.spotless.SpotlessExtension

plugins {
  `java-library`
  id("kotlin-spring")
  id("ch.ayedo.jooqmodelator") version "3.5.0"
}

afterEvaluate {
  tasks.getByName("compileKotlin").dependsOn("generateJooqMetamodel")
}

sourceSets {
  main {
    java {
      srcDir("$projectDir/src/generated/java")
    }
  }
}

tasks.getByName<Delete>("clean") {
  delete.add("$projectDir/src/generated/java")
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

  jooqModelatorRuntime(platform("com.netflix.spinnaker.kork:kork-bom:${property("korkVersion")}"))
  jooqModelatorRuntime("mysql:mysql-connector-java")
}

jooqModelator {
  jooqVersion = "3.11.11"
  jooqEdition = "OSS"
  jooqConfigPath = "$projectDir/src/main/resources/jooqConfig.xml"
  jooqOutputPath = "$projectDir/src/generated/java"
  migrationEngine = "LIQUIBASE"
  migrationsPaths = listOf("$projectDir/src/main/resources/db")
  dockerTag = "mysql/mysql-server:5.7"
  dockerEnv = listOf("MYSQL_ROOT_PASSWORD=sa", "MYSQL_ROOT_HOST=%", "MYSQL_DATABASE=keel")
  dockerHostPort = 3306
  dockerContainerPort = 3306
}

// Don't enforce spotless for generated code
afterEvaluate {
  configure<SpotlessExtension> {
    java {
      targetExclude(fileTree("$projectDir/src/generated/java"))
    }
  }
}
