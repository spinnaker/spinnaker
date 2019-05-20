plugins {
  `java-platform`
  `maven-publish`
}

// without this building the pom fails when using the Nebula publishing plugin
configurations {
  create("compileOnly")
}

javaPlatform {
  allowDependencies()
}

if (property("enablePublishing") == "true") {
  publishing {
    publications {
      named<MavenPublication>("nebula") {
        from(components["javaPlatform"])
      }
    }
  }
}

dependencies {
  api(platform("com.netflix.spinnaker.kork:kork-bom:${property("korkVersion")}"))
  constraints {
    rootProject
      .subprojects
      .filter { it != project }
      .forEach { api(project(it.path)) }
  }
}
