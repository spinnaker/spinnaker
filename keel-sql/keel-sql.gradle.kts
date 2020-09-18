import com.diffplug.gradle.spotless.SpotlessExtension
import org.liquibase.gradle.LiquibaseTask
import java.lang.Thread.sleep

val buildingInDocker = project.properties["buildingInDocker"]?.toString().let { it == "true" }

plugins {
  `java-library`
  id("kotlin-spring")
  id("nu.studer.jooq") version "5.0.1"
  id("org.liquibase.gradle") version "2.0.4"
}

afterEvaluate {
  tasks.getByName("compileKotlin") {
    dependsOn("jooqGenerate")
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

  jooqGenerator(platform("com.netflix.spinnaker.kork:kork-bom:${property("korkVersion")}"))
  jooqGenerator("mysql:mysql-connector-java")
  jooqGenerator("org.jooq:jooq-meta-extensions")
  jooqGenerator("ch.qos.logback:logback-classic")

  liquibaseRuntime(platform("com.netflix.spinnaker.kork:kork-bom:${property("korkVersion")}"))
  liquibaseRuntime("org.liquibase:liquibase-core")
  liquibaseRuntime("ch.qos.logback:logback-classic")
  liquibaseRuntime("org.yaml:snakeyaml")
  liquibaseRuntime("mysql:mysql-connector-java")
}

// expand properties in jooqConfig.xml so it gets a fully-qualified directory to generate into
tasks.withType<ProcessResources> {
  filesMatching("jooqConfig.xml") {
    expand(project.properties)
  }
}

tasks.getByName<LiquibaseTask>("liquibaseUpdate") {
  inputs.dir("$projectDir/src/main/resources/db")
  outputs.dir("$projectDir/src/generated/java")

  doFirst {
    if (!buildingInDocker) {
      exec {
        commandLine("sh", "-c", "docker stop mysqlJooq >/dev/null 2>&1 || true")
      }
      exec {
        commandLine("sh", "-c", "docker run --name mysqlJooq --health-cmd='mysqladmin ping -s' -d --rm -e MYSQL_ROOT_PASSWORD=sa -e MYSQL_DATABASE=keel -p 6603:3306 mysql:5.7 >/dev/null 2>&1; while STATUS=\$(docker inspect --format \"{{.State.Health.Status}}\" mysqlJooq); [ \$STATUS != \"healthy\" ]; do if [ \$STATUS = \"unhealthy\" ]; then echo \"Docker failed to start\"; exit -1; fi; sleep 1; done")
      }
    }
  }
}

// Task used when building in Docker in place of jooqModelator (see Dockerfile.compile)
tasks.register("jooqGenerate") {
  group = "Execution"
  description = "Run the jOOQ code generation tool"

  dependsOn("processResources", "liquibaseUpdate")

  inputs.dir("$projectDir/src/main/resources/db")
  outputs.dir("$projectDir/src/generated/java")

  doLast {
    javaexec {
      classpath = configurations.named("jooqGenerator").get()
      main = "org.jooq.codegen.GenerationTool"
      args = listOf("$buildDir/resources/main/jooqConfig.xml")
    }
    if (!buildingInDocker) {
      exec {
        commandLine("sh", "-c", "docker stop mysqlJooq >/dev/null 2>&1 || true")
      }
    }
  }
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
  activities.register("local") {
    arguments = mapOf(
      "logLevel" to "info",
      "changeLogFile" to "src/main/resources/db/databaseChangeLog.yml",
      "url" to "jdbc:mysql://localhost:3306/keel?useSSL=false&serverTimezone=UTC",
      "username" to "root",
      "password" to ""
    )
  }
  activities.register("docker") {
    arguments = mapOf(
      "logLevel" to "info",
      "changeLogFile" to "src/main/resources/db/databaseChangeLog.yml",
      "url" to "jdbc:mysql://127.0.0.1:6603/keel?useSSL=false&serverTimezone=UTC",
      "username" to "root",
      "password" to "sa"
    )
  }
  runList = "docker"
}
