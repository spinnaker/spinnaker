plugins {
  `java-library`
  id("kotlin-spring")
  application
}

repositories {
  maven {
    url = uri("https://kotlin.bintray.com/kotlinx")
  }
}

sourceSets {
  main {
    dependencies {
      implementation("org.jetbrains.kotlinx:kotlinx-cli:0.3")
    }
  }
}

dependencies {
  implementation(project(":keel-core"))
  implementation(project(":keel-sql"))
  implementation(project(":keel-ec2-plugin"))
  implementation("org.springframework.boot:spring-boot-starter")
  implementation("org.springframework.boot:spring-boot-autoconfigure")
}
