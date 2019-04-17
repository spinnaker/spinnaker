plugins {
  `java-library`
  id("kotlin-spring")
}

dependencies {
  api(project(":keel-retrofit"))
  api("com.fasterxml.jackson.core:jackson-databind")
  api("com.fasterxml.jackson.core:jackson-annotations")
  api("com.fasterxml.jackson.module:jackson-module-kotlin")
  api("com.fasterxml.jackson.datatype:jackson-datatype-jsr310")
  api(project(":keel-core-test"))

  implementation("org.junit.jupiter:junit-jupiter-api")
  implementation("com.squareup.retrofit2:retrofit-mock")
  implementation("com.squareup.okhttp3:mockwebserver")
  implementation("dev.minutest:minutest")
  implementation("io.strikt:strikt-core")

  runtime("org.jetbrains.kotlin:kotlin-reflect")
}
