package com.netflix.spinnaker.keel.api.ec2

import com.netflix.spinnaker.keel.diff.DefaultResourceDiff
import de.danielbechler.diff.node.DiffNode
import de.danielbechler.diff.node.DiffNode.State.ADDED
import de.danielbechler.diff.node.DiffNode.State.CHANGED
import de.danielbechler.diff.node.DiffNode.State.UNTOUCHED
import dev.minutest.junit.JUnit5Minutests
import dev.minutest.rootContext
import org.apache.commons.lang3.RandomStringUtils.randomAlphanumeric
import org.apache.commons.lang3.RandomStringUtils.randomNumeric
import strikt.api.expectThat
import strikt.assertions.isEqualTo

internal class ServerGroupDiffTests : JUnit5Minutests {

  data class Fixture(
    val desired: Map<String, ServerGroup>,
    val current: Map<String, ServerGroup>? = null
  )

  val Fixture.diff: DefaultResourceDiff<Map<String, ServerGroup>>
    get() = DefaultResourceDiff(desired, current)

  val Fixture.state: DiffNode.State
    get() = diff.diff.state

  fun Map<String, ServerGroup>.withSequenceNumbers(): Map<String, ServerGroup> =
    mapValues { (_, serverGroup) ->
      serverGroup.copy(name = "${serverGroup.name}-v${randomNumeric(3)}")
    }

  fun Fixture.withMatchingCurrentClusters(): Fixture =
    Fixture(
      desired,
      desired.withSequenceNumbers()
    )

  fun Fixture.withOneClusterOutOfDate(diffRegion: String): Fixture =
    Fixture(
      desired,
      desired.mapValues { (region, serverGroup) ->
        if (region == diffRegion) {
          serverGroup.copy(launchConfiguration = serverGroup.launchConfiguration.copy(
            imageId = "ami-${randomAlphanumeric(7)}",
            appVersion = "fnord-0.0.9.h22.${randomNumeric(6)}"
          ))
        } else {
          serverGroup
        }
      }
        .withSequenceNumbers()
    )

  fun tests() = rootContext<Fixture> {
    context("desire server groups in 2 regions") {
      fixture {
        Fixture(
          desired = setOf("ap-south-1", "me-south-1").associateWith { region ->
            ServerGroup(
              name = "fnord-main",
              location = Location(
                account = "prod",
                vpc = "vpc0",
                region = region,
                subnet = "internal",
                availabilityZones = setOf("a", "b", "c").map { "$region$it" }.toSet()
              ),
              launchConfiguration = LaunchConfiguration(
                imageId = "ami-${randomAlphanumeric(7)}",
                appVersion = "fnord-1.0.0.h23.${randomNumeric(6)}",
                baseImageVersion = "nflx-base-5.308.0-h1044.b4b3f78",
                instanceType = "r5.4xlarge",
                ebsOptimized = true,
                iamRole = "fnordInstanceRole",
                keyPair = "fnordKeyPair"
              )
            )
          }
        )
      }

      context("currently none exist") {
        test("there is a diff") {
          expectThat(state).isEqualTo(ADDED)
        }
      }

      context("both server groups match") {
        deriveFixture {
          withMatchingCurrentClusters()
        }

        test("there is no diff") {
          expectThat(state).isEqualTo(UNTOUCHED)
        }
      }

      context("one server group differs") {
        deriveFixture {
          withOneClusterOutOfDate("me-south-1")
        }

        test("there is no diff") {
          expectThat(state).isEqualTo(CHANGED)
        }
      }
    }
  }
}
