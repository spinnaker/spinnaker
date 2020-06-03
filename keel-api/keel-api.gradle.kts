plugins {
  `java-library`
}

/*
 * DO NOT ADD ANY NON-TEST DEPENDENCIES HERE!
 *
 * This module should be the only thing required by plugin implementors. In order to
 * avoid dependency conflicts we should bring the bare minimum of transitive
 * dependencies along for the ride -- ideally nothing at all.
 */
dependencies {
  api("com.netflix.spinnaker.kork:kork-plugins-api:${property("korkVersion")}")
  api("com.netflix.spinnaker.kork:kork-exceptions:${property("korkVersion")}")
  api("de.huxhorn.sulky:de.huxhorn.sulky.ulid")
  testImplementation("io.strikt:strikt-core")
  testImplementation("dev.minutest:minutest")
}
