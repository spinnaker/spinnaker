plugins {
  kotlin("jvm")
}

dependencies {
  api(project(":keel-api"))

  implementation("org.jetbrains.kotlin:kotlin-reflect")
  implementation("com.fasterxml.jackson.core:jackson-annotations")

  testImplementation(project(":keel-core"))
  testImplementation("com.fasterxml.jackson.core:jackson-databind")
  testImplementation("com.fasterxml.jackson.module:jackson-module-kotlin")
  testImplementation("io.strikt:strikt-core")
}
