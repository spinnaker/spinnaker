import ch.ayedo.jooqmodelator.gradle.JooqModelatorTask
import com.diffplug.gradle.spotless.SpotlessExtension

plugins {
  `java-library`
  id("kotlin-spring")
  id("ch.ayedo.jooqmodelator") version "3.9.0"
  id("org.liquibase.gradle") version "2.0.4"
}

/**
 *  Workaround to enable composite build -- jOOQ uses the output path in its config XML file relative to current
 * working directory, which in the composite build is not keel-sql. This causes the generated files to be placed
 * in the root of the parent project. This task copies them over to the right place, and will only execute in the
 * context of a composite build.
 */
tasks.register<Copy>("copyGeneratedJooqFiles") {
  doFirst {
    logger.lifecycle("Running from composite build. Copying jOOQ generated files from parent project.")
    logger.lifecycle("From: ${project.gradle.parent?.rootProject?.projectDir}/keel-sql/src/generated/java")
    logger.lifecycle("  To: $projectDir/src/generated/java")
  }
  from("${project.gradle.parent?.rootProject?.projectDir}/keel-sql/src/generated/java")
  into("$projectDir/src/generated/java")
  dependsOn("generateJooqMetamodel")
  onlyIf { project.gradle.parent != null }
}

afterEvaluate {
  tasks.getByName("compileKotlin") {
    dependsOn("copyGeneratedJooqFiles")
  }
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
  implementation(project(":keel-artifact"))
  implementation("com.netflix.spinnaker.kork:kork-sql")
  implementation("org.springframework:spring-jdbc")
  implementation("org.springframework:spring-tx")
  implementation("org.jooq:jooq")
  implementation("com.zaxxer:HikariCP")
  implementation("org.liquibase:liquibase-core")
  implementation("com.netflix.spinnaker.kork:kork-sql")

  runtimeOnly("mysql:mysql-connector-java")

  testImplementation("com.netflix.spinnaker.kork:kork-sql-test")
  testImplementation("io.strikt:strikt-core")
  testImplementation(project(":keel-spring-test-support"))
  testImplementation(project(":keel-test"))
  testImplementation(project(":keel-core-test"))
  testImplementation(project(":keel-web")) {
    // avoid circular dependency which breaks Liquibase
    exclude(module = "keel-sql")
  }
  testImplementation("org.testcontainers:mysql")
  testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-core")

  jooqModelatorRuntime(platform("com.netflix.spinnaker.kork:kork-bom:${property("korkVersion")}"))
  jooqModelatorRuntime("mysql:mysql-connector-java")

  liquibaseRuntime(platform("com.netflix.spinnaker.kork:kork-bom:${property("korkVersion")}"))
  liquibaseRuntime("mysql:mysql-connector-java")
}

jooqModelator {
  jooqVersion = "3.13.2"
  jooqEdition = "OSS"
  jooqConfigPath = "$buildDir/resources/main/jooqConfig.xml"
  jooqOutputPath = "$projectDir/src/generated/java"
  migrationEngine = "LIQUIBASE"
  migrationsPaths = listOf("$projectDir/src/main/resources/db")
  dockerTag = "mysql/mysql-server:5.7"
  dockerEnv = listOf("MYSQL_ROOT_PASSWORD=sa", "MYSQL_ROOT_HOST=%", "MYSQL_DATABASE=keel")
  dockerHostPort = 6603
  dockerContainerPort = 3306
}

// expand properties in jooqConfig.xml so it gets a fully-qualified directory to generate into
tasks.withType<ProcessResources> {
  filesMatching("jooqConfig.xml") {
    expand(project.properties)
  }
}

// process resources before generating JOOQ stuff so we tokenize the config XML
tasks.withType<JooqModelatorTask> {
  dependsOn("processResources")
}

// Don't enforce spotless for generated code
afterEvaluate {
  configure<SpotlessExtension> {
    java {
      targetExclude(fileTree("$projectDir/src/generated/java"))
    }
  }
}

liquibase {
  activities.register("main") {
    arguments = mapOf(
      "logLevel" to "info",
      "changeLogFile" to "db/databaseChangeLog.yml",
      "url" to "jdbc:mysql://localhost:3306/keel?useSSL=false&serverTimezone=UTC",
      "username" to "root",
      "password" to ""
    )
  }
  runList = "main"
}
