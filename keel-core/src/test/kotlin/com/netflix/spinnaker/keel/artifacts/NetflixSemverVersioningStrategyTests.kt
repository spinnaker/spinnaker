package com.netflix.spinnaker.keel.artifacts

import com.netflix.spinnaker.keel.api.artifacts.PublishedArtifact
import dev.minutest.junit.JUnit5Minutests
import dev.minutest.rootContext
import strikt.api.expectThat
import strikt.assertions.isEqualTo
import strikt.assertions.isNull

class NetflixSemverVersioningStrategyTests : JUnit5Minutests {
  fun tests() = rootContext<NetflixSemVerVersioningStrategy> {
    fixture {
      NetflixSemVerVersioningStrategy
    }

    context("valid version strings") {
      mapOf(
        "1.0.0" to Pair(null, null),
        "1.0.0-3" to Pair(3, null),
        "1.0.0-h3" to Pair(3, null),
        "1.0.0-dev.3" to Pair(3, null),
        "1.0.0-snapshot.3" to Pair(3, null),
        "1.0.0-rc.3" to Pair(3, null),
        "1.0.0-rel.3" to Pair(3, null),
        "1.0.0-final.3" to Pair(3, null),
        "1.0.0~dev.3" to Pair(3, null),
        "1.0.0~snapshot.3" to Pair(3, null),
        "1.0.0~rc.3" to Pair(3, null),
        "1.0.0~rel.3" to Pair(3, null),
        "1.0.0~final.3" to Pair(3, null),
        "1.0.0-6fbf05f078e2fd0ca75341bdaf3206a16beeb328" to Pair(null, "6fbf05f078e2fd0ca75341bdaf3206a16beeb328"),
        "1.0.0-3-6fbf05f078e2fd0ca75341bdaf3206a16beeb328" to Pair(3, "6fbf05f078e2fd0ca75341bdaf3206a16beeb328"),
        "1.0.0-h3-6fbf05f078e2fd0ca75341bdaf3206a16beeb328" to Pair(3, "6fbf05f078e2fd0ca75341bdaf3206a16beeb328"),
        "1.0.0-dev.12-f89a05bfb44d54227f3cebaec59230a6077c0505" to Pair(12, "f89a05bfb44d54227f3cebaec59230a6077c0505"),
        "1.0.0-dev-12-f89a05bfb44d54227f3cebaec59230a6077c0505" to Pair(12, "f89a05bfb44d54227f3cebaec59230a6077c0505"),
        "1.0.0~dev.15-4cbd040533a2f43fc6691d773d510cda70f4126a" to Pair(15, "4cbd040533a2f43fc6691d773d510cda70f4126a"),
        "1.0.0~snapshot.15-4cbd040533a2f43fc6691d773d510cda70f4126a" to Pair(15, "4cbd040533a2f43fc6691d773d510cda70f4126a"),
        "1.0.0~rc.15-4cbd040533a2f43fc6691d773d510cda70f4126a" to Pair(15, "4cbd040533a2f43fc6691d773d510cda70f4126a"),
        "1.0.0~rel.15-4cbd040533a2f43fc6691d773d510cda70f4126a" to Pair(15, "4cbd040533a2f43fc6691d773d510cda70f4126a"),
        "1.0.0~final.15-4cbd040533a2f43fc6691d773d510cda70f4126a" to Pair(15, "4cbd040533a2f43fc6691d773d510cda70f4126a")
      ).forEach { version, (build, commit) ->
        val artifact = PublishedArtifact("test", "DEB", "test", version)

        test("returns expected build number for version $version") {
          expectThat(getBuildNumber(artifact)).isEqualTo(build)
        }

        test("returns expected commit hash for version $version") {
          expectThat(getCommitHash(artifact)).isEqualTo(commit)
        }
      }
    }

    context("invalid version strings") {
      listOf(
        "1.0.0-i3",
        "1.0.0-foo.12-f89a05bfb44d54227f3cebaec59230a6077c0505",
        "1.0.0-bar-12-f89a05bfb44d54227f3cebaec59230a6077c0505",
        "1.0.0-foo-12-banana",
        "1.0.0-bar-12-123456",
        "1.0.0beta"
      ).forEach { version ->
        val artifact = PublishedArtifact("test", "DEB", "test", version)

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