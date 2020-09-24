package com.netflix.spinnaker.keel.artifacts

import com.netflix.spinnaker.keel.api.artifacts.ArtifactVersion
import dev.minutest.junit.JUnit5Minutests
import dev.minutest.rootContext
import strikt.api.expectThat
import strikt.assertions.isEqualTo
import strikt.assertions.isNull

class NetflixVersionsTests : JUnit5Minutests {
  fun tests() = rootContext<NetflixVersions> {
    fixture {
      NetflixVersions
    }

    context("valid version strings") {
      mapOf(
        // simple versions
        "1.0.0" to Triple("1.0.0",null, null),
        // pre-release versions
        "1.0.0-dev.3" to Triple("1.0.0-dev.3",null, null),
        "1.0.0-snapshot.3" to Triple("1.0.0-snapshot.3",null, null),
        "1.0.0-rc.3" to Triple("1.0.0-rc.3",null, null),
        // versions with build number
        "1.0.0-3" to Triple("1.0.0",3, null),
        "1.0.0-h3" to Triple("1.0.0",3, null),
        // versions with build number and commit hash
        "1.0.0-3.6fbf05f078e2fd0ca75341bdaf3206a16beeb328" to Triple("1.0.0",3, "6fbf05f078e2fd0ca75341bdaf3206a16beeb328"),
        "1.0.0-h3.6fbf05f078e2fd0ca75341bdaf3206a16beeb328" to Triple("1.0.0",3, "6fbf05f078e2fd0ca75341bdaf3206a16beeb328"),
        // pre-release versions with build number and commit hash
        "1.0.0-dev-12.f89a05bfb44d54227f3cebaec59230a6077c0505" to Triple("1.0.0-dev",12, "f89a05bfb44d54227f3cebaec59230a6077c0505"),
        "1.0.0-dev-h12.f89a05bfb44d54227f3cebaec59230a6077c0505" to Triple("1.0.0-dev",12, "f89a05bfb44d54227f3cebaec59230a6077c0505"),
        "1.0.0-dev.3-12.f89a05bfb44d54227f3cebaec59230a6077c0505" to Triple("1.0.0-dev.3",12, "f89a05bfb44d54227f3cebaec59230a6077c0505"),
        "1.0.0-dev.3-h12.f89a05bfb44d54227f3cebaec59230a6077c0505" to Triple("1.0.0-dev.3",12, "f89a05bfb44d54227f3cebaec59230a6077c0505"),
        "1.0.0~dev-h15.4cbd040533a2f43fc6691d773d510cda70f4126a" to Triple("1.0.0~dev",15, "4cbd040533a2f43fc6691d773d510cda70f4126a"),
        "1.0.0~snapshot.1-15.4cbd040533a2f43fc6691d773d510cda70f4126a" to Triple("1.0.0~snapshot.1",15, "4cbd040533a2f43fc6691d773d510cda70f4126a"),
        "1.0.0~rc.2-h15.4cbd040533a2f43fc6691d773d510cda70f4126a" to Triple("1.0.0~rc.2",15, "4cbd040533a2f43fc6691d773d510cda70f4126a")
      ).forEach { version, (displayName, build, commit) ->
        val artifact = ArtifactVersion("test", "DEB", "test", version)

        // check with and without debian package name prefix
        listOf("", "mydebian-").forEach { prefix ->
          test("returns expected display name for version $prefix$version") {
            expectThat(getVersionDisplayName(artifact)).isEqualTo(displayName)
          }

          test("returns expected build number for version $prefix$version") {
            expectThat(getBuildNumber(artifact)).isEqualTo(build)
          }

          test("returns expected commit hash for version $prefix$version") {
            expectThat(getCommitHash(artifact)).isEqualTo(commit)
          }
        }
      }
    }

    context("invalid version strings") {
      listOf(
        "1.0.0-i3",
        "1.0.0-foo-12.f89a05bfb44d54227f3cebaec59230a6077c0505",
        "1.0.0-foo.1-12.f89a05bfb44d54227f3cebaec59230a6077c0505",
        "1.0.0-foo-12.banana",
        "1.0.0-foo-h12.banana",
        "1.0.0-foo-h12.123456",
        "1.0.0beta"
      ).forEach { version ->
        val artifact = ArtifactVersion("test", "DEB", "test", version)

        test("returns null build number for version $version") {
          expectThat(getBuildNumber(artifact)).isNull()
        }

        test("returns null commit hash for version $version") {
          expectThat(getCommitHash(artifact)).isNull()
        }
      }
    }
  }
}