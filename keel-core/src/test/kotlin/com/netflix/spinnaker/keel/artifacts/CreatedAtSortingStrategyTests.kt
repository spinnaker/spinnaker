package com.netflix.spinnaker.keel.artifacts

import com.netflix.spinnaker.keel.api.artifacts.ArtifactOriginFilterSpec
import com.netflix.spinnaker.keel.api.artifacts.BranchFilterSpec
import com.netflix.spinnaker.keel.api.artifacts.DEBIAN
import com.netflix.spinnaker.keel.api.artifacts.GitMetadata
import com.netflix.spinnaker.keel.api.artifacts.PublishedArtifact
import com.netflix.spinnaker.keel.api.artifacts.VirtualMachineOptions
import com.netflix.spinnaker.time.MutableClock
import dev.minutest.junit.JUnit5Minutests
import dev.minutest.rootContext
import strikt.api.expectThat
import strikt.assertions.isEqualTo

class CreatedAtSortingStrategyTests : JUnit5Minutests {
  object Fixture {
    val clock = MutableClock()

    val debianFilteredByBranch =  DebianArtifact(
      name = "keeldemo",
      deliveryConfigName = "my-manifest",
      reference = "feature-branch",
      vmOptions = VirtualMachineOptions(baseOs = "bionic", regions = setOf("us-west-2")),
      from = ArtifactOriginFilterSpec(
        branch = BranchFilterSpec(
          name = "my-feature-branch"
        )
      )
    )

    val debianFilteredByPullRequest = DebianArtifact(
      name = "keeldemo",
      deliveryConfigName = "my-manifest",
      reference = "pr",
      vmOptions = VirtualMachineOptions(baseOs = "bionic", regions = setOf("us-west-2")),
      from = ArtifactOriginFilterSpec(
        pullRequestOnly = true
      )
    )

    val versions = (0..10).map {
      clock.tickMinutes(1)
      PublishedArtifact("keeldemo", DEBIAN, "1.0.$it", createdAt = clock.instant(),
        gitMetadata = GitMetadata("commit", branch = if (it % 2 == 0) "my-feature-branch" else "master")
      )
    }

    val subject = CreatedAtSortingStrategy
  }

  fun tests() = rootContext<Fixture> {
    fixture { Fixture }

    context("an artifact filtered by branch or pull request") {
      test("branch and timestamp sorting strategy is auto-selected") {
        expectThat(debianFilteredByBranch.sortingStrategy)
          .isEqualTo(CreatedAtSortingStrategy)

        expectThat(debianFilteredByPullRequest.sortingStrategy)
          .isEqualTo(CreatedAtSortingStrategy)
      }

      test("artifact versions are sorted by descending order of creation timestamp") {
        expectThat(versions.shuffled().sortedWith(subject.comparator))
          .isEqualTo(versions.sortedByDescending { it.createdAt })
      }
    }
  }
}