package com.netflix.spinnaker.keel.artifacts

import com.netflix.spinnaker.keel.api.artifacts.TagVersionStrategy.BRANCH_JOB_COMMIT_BY_JOB
import com.netflix.spinnaker.keel.api.artifacts.TagVersionStrategy.INCREASING_TAG
import com.netflix.spinnaker.keel.api.artifacts.TagVersionStrategy.SEMVER_JOB_COMMIT_BY_JOB
import com.netflix.spinnaker.keel.api.artifacts.TagVersionStrategy.SEMVER_JOB_COMMIT_BY_SEMVER
import com.netflix.spinnaker.keel.api.artifacts.TagVersionStrategy.SEMVER_TAG
import com.netflix.spinnaker.keel.core.DEBIAN_VERSION_COMPARATOR
import dev.minutest.junit.JUnit5Minutests
import dev.minutest.rootContext
import strikt.api.expectThat
import strikt.assertions.isEqualTo

/**
 * Ensure that every comparator we use sorts in descending order by default.
 */
class ComparatorSortingTests : JUnit5Minutests {

  private val debianVersions = listOf("fnord-1.0.0-41595c4", "fnord-2.1.0-18ed1dc", "fnord-2.0.0-608bd90")
  private val incrTags = listOf("1", "2", "3", "0")
  private val semverTags = listOf("v0.0.3", "v0.1.3", "v0.10.3", "v0.4.1")
  private val branchJobCommitTags = listOf("master-h1.blah", "master-h2.blah", "master-h3.blah", "master-h0.blah")
  private val semverJobCommitTags = listOf("v1.12.1-h1188.35b8b29", "v1.12.3-rc.1-h1192.f876e5a", "v1.12.2-h1182.8a5b962")

  fun tests() = rootContext<Unit> {
    context("deb semantic version") {
      test("descending by default") {
        expectThat(debianVersions.sortedWith(DEBIAN_VERSION_COMPARATOR).first()).isEqualTo("fnord-2.1.0-18ed1dc")
      }
    }

    context("increasing version") {
      test("descending by default") {
        expectThat(incrTags.sortedWith(DockerVersioningStrategy(INCREASING_TAG).comparator).first()).isEqualTo("3")
      }
    }

    context("semver version") {
      test("descending by default") {
        expectThat(semverTags.sortedWith(DockerVersioningStrategy(SEMVER_TAG).comparator).first()).isEqualTo("v0.10.3")
      }
    }

    context("branchjobcommit version") {
      test("descending by default") {
        expectThat(branchJobCommitTags.sortedWith(DockerVersioningStrategy(BRANCH_JOB_COMMIT_BY_JOB).comparator).first()).isEqualTo("master-h3.blah")
      }
    }

    context("semver job commit version") {
      test("descending by default, by job") {
        expectThat(semverJobCommitTags.sortedWith(DockerVersioningStrategy(SEMVER_JOB_COMMIT_BY_JOB).comparator).first()).isEqualTo("v1.12.3-rc.1-h1192.f876e5a")
      }

      test("descending by default, by version") {
        expectThat(semverJobCommitTags.sortedWith(DockerVersioningStrategy(SEMVER_JOB_COMMIT_BY_SEMVER).comparator).first()).isEqualTo("v1.12.3-rc.1-h1192.f876e5a")
      }
    }
  }
}
