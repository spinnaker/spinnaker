plugins {
  `java-platform`
}

javaPlatform {
  allowDependencies()
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
