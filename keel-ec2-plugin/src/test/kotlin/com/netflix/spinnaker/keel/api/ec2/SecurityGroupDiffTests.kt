package com.netflix.spinnaker.keel.api.ec2

import com.netflix.spinnaker.keel.api.Moniker
import com.netflix.spinnaker.keel.api.ec2.SecurityGroupRule.Protocol.TCP
import com.netflix.spinnaker.keel.diff.DefaultResourceDiff
import de.danielbechler.diff.node.DiffNode
import de.danielbechler.diff.node.DiffNode.State.ADDED
import de.danielbechler.diff.node.DiffNode.State.UNTOUCHED
import dev.minutest.junit.JUnit5Minutests
import dev.minutest.rootContext
import strikt.api.expectThat
import strikt.assertions.isEqualTo
import com.netflix.spinnaker.keel.api.ec2.SecurityGroup.Location as SecurityGroupLocation

internal class SecurityGroupDiffTests : JUnit5Minutests {

  data class Fixture(
    val desired: Map<String, SecurityGroup>,
    val current: Map<String, SecurityGroup>? = null
  )

  private val referenceRule: ReferenceRule = ReferenceRule(
    protocol = TCP,
    name = "some-other-sg",
    portRange = PortRange(
      startPort = 443,
      endPort = 443
    )
  )

  private val crossAccountReferenceRule: CrossAccountReferenceRule = CrossAccountReferenceRule(
    protocol = TCP,
    name = "some-other-sg",
    account = "some-other-account",
    vpc = "vpc0",
    portRange = PortRange(
      startPort = 443,
      endPort = 443
    )
  )

  private val cidrRule: CidrRule = CidrRule(
    protocol = TCP,
    blockRange = "10.0.0.0/8",
    portRange = PortRange(
      startPort = 443,
      endPort = 443
    )
  )

  private val Fixture.diff: DefaultResourceDiff<Map<String, SecurityGroup>>
    get() = DefaultResourceDiff(desired, current)

  private val Fixture.state: DiffNode.State
    get() = diff.diff.state

  private fun Fixture.withMatchingCurrentState(): Fixture =
    Fixture(
      desired = desired,
      current = desired
    )

  private fun Fixture.withIgnorableDifference(): Fixture =
    Fixture(
      desired = desired,
      current = desired.mapValues { (_, securityGroup) ->
        securityGroup.copy(description = "whatever")
      }
    )

  private fun Fixture.withIgnorableDifferenceInCidrRule(): Fixture =
    Fixture(
      desired = desired,
      current = desired.mapValues { (_, securityGroup) ->
        securityGroup.copy(
          inboundRules = securityGroup.inboundRules
            .toMutableSet()
            .apply { removeIf { it is CidrRule } }
            .apply { add(cidrRule.copy(description = "whatever")) }
            .toSet()
        )
      }
    )

  fun tests() = rootContext<Fixture> {
    context("desire security group with all types of inbound rules") {
      fixture {
        Fixture(
          desired = mapOf(
            "us-west-2" to
              SecurityGroup(
                description = "test",
                moniker = Moniker(
                  app = "fnord",
                  stack = "test"
                ),
                location = SecurityGroupLocation(
                  account = "test",
                  vpc = "vpc0",
                  region = "us-west-2"
                ),
                inboundRules = setOf(referenceRule, crossAccountReferenceRule, cidrRule)
              )
          )
        )
      }

      context("currently none exist") {
        test("there is a diff") {
          expectThat(state).isEqualTo(ADDED)
        }
      }

      context("current security group matches") {
        deriveFixture {
          withMatchingCurrentState()
        }

        test("there is no diff") {
          expectThat(state).isEqualTo(UNTOUCHED)
        }
      }

      context("ignorable difference in current security group") {
        deriveFixture {
          withIgnorableDifference()
        }

        test("there is no diff") {
          expectThat(state).isEqualTo(UNTOUCHED)
        }
      }

      context("ignorable difference in current security group's CIDR rule") {
        deriveFixture {
          withIgnorableDifferenceInCidrRule()
        }

        test("there is no diff") {
          expectThat(state).isEqualTo(UNTOUCHED)
        }
      }
    }
  }
}
