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
    api(project(":keel-api"))
    api(project(":keel-artifact"))
    api(project(":keel-bakery-plugin"))
    api(project(":keel-clouddriver"))
    api(project(":keel-core"))
    api(project(":keel-core-test"))
    api(project(":keel-deliveryconfig-plugin"))
    api(project(":keel-ec2-plugin"))
    api(project(":keel-eureka"))
    api(project(":keel-front50"))
    api(project(":keel-igor"))
    api(project(":keel-mahe"))
    api(project(":keel-orca"))
    api(project(":keel-plugin"))
    api(project(":keel-redis"))
    api(project(":keel-retrofit"))
    api(project(":keel-retrofit-test-support"))
    api(project(":keel-spring-test-support"))
    api(project(":keel-sql"))
    api(project(":keel-veto"))
  }
}
