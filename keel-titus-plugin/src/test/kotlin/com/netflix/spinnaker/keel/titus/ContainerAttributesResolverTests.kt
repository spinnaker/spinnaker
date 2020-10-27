package com.netflix.spinnaker.keel.titus

import com.netflix.spinnaker.keel.api.Moniker
import com.netflix.spinnaker.keel.api.Resource
import com.netflix.spinnaker.keel.api.SimpleLocations
import com.netflix.spinnaker.keel.api.SimpleRegionSpec
import com.netflix.spinnaker.keel.api.plugins.supporting
import com.netflix.spinnaker.keel.api.titus.TitusClusterSpec
import com.netflix.spinnaker.keel.api.titus.TitusServerGroupSpec
import com.netflix.spinnaker.keel.clouddriver.CloudDriverService
import com.netflix.spinnaker.keel.docker.ReferenceProvider
import com.netflix.spinnaker.keel.test.resource
import dev.minutest.junit.JUnit5Minutests
import dev.minutest.rootContext
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import org.springframework.core.env.Environment
import strikt.api.expect
import strikt.api.expectThat
import strikt.assertions.containsExactly
import strikt.assertions.isEqualTo
import strikt.assertions.isNull

internal class ContainerAttributesResolverTests : JUnit5Minutests {

  val accountKey = "titus.account"
  val subnetKey = "titus.subnet"
  val eastSubnets = "subnet-east-1, subnet-east-2"
  val westSubnets = "subnet-west-3, subnet-west-4"
  val account = "titus"
  val awsAccountId = "1234"

  val defaults = mockk<DefaultContainerAttributes> {
    every { getAccountKey() } returns accountKey
    every { getSubnetKey() } returns subnetKey
    every { getSubnetValue(account, "east")} returns eastSubnets
    every { getSubnetValue(account, "west")} returns westSubnets
    every { getSubnetValue(account, "south")} returns null
  }

  val baseSpec = TitusClusterSpec(
    moniker = Moniker(app = "keel", stack = "test"),
    locations = SimpleLocations(
      account = account,
      regions = setOf(SimpleRegionSpec("east"), SimpleRegionSpec("west"))
    ),
    container = ReferenceProvider("my-artifact"),
    _defaults = TitusServerGroupSpec(),
    overrides = emptyMap()
  )

  val clouddriverService = mockk<CloudDriverService>() {
    coEvery { getAccountInformation(account, any()) } returns mapOf("awsAccount" to "aws")
    coEvery { getAccountInformation("aws", any()) } returns mapOf("accountId" to awsAccountId)
  }
  val springEnv: Environment = mockk(relaxed = true) {
    coEvery { getProperty("keel.titus.resolvers.container-attributes.enabled", Boolean::class.java, true) } returns true
  }

  data class Fixture(val subject: ContainerAttributesResolver, val spec: TitusClusterSpec) {
    val resource = resource(
      kind = TITUS_CLUSTER_V1.kind,
      spec = spec
    )
    val resolved by lazy { subject(resource) }
  }

  fun tests() = rootContext<Fixture> {
    context("basic test") {
      fixture {
        Fixture(
          ContainerAttributesResolver(defaults, clouddriverService, springEnv),
          baseSpec
        )
      }

      test("supports the resource kind") {
        expectThat(listOf(subject).supporting(resource))
          .containsExactly(subject)
      }

      context("spec has no defaults set") {
        test("account and subnet are set") {
          validateKeysSet(resolved)
        }
      }
    }

    context("account is set") {
      fixture {
        Fixture(
          ContainerAttributesResolver(defaults, clouddriverService, springEnv),
          baseSpec.copy(_defaults = baseSpec.defaults.copy(containerAttributes = mapOf(accountKey to awsAccountId)))
        )
      }

      test("account is set, subnets get added"){
        validateKeysSet(resolved)
      }
    }

    context("non-default values are present") {
      fixture {
        Fixture(
          ContainerAttributesResolver(defaults, clouddriverService, springEnv),
          baseSpec.copy(_defaults = baseSpec.defaults.copy(containerAttributes = mapOf(accountKey to "blah", subnetKey to "fake-subnets")))
        )
      }

      test("we leave the values that are set") {
        val resolvedEast = resolved.spec.resolveContainerAttributes("east")
        val resolvedWest = resolved.spec.resolveContainerAttributes("west")
        expect {
          that(resolvedEast[accountKey]).isEqualTo("blah")
          that(resolvedEast[subnetKey]).isEqualTo("fake-subnets")
          that(resolvedWest[accountKey]).isEqualTo("blah")
          that(resolvedWest[subnetKey]).isEqualTo("fake-subnets")
        }
      }
    }

    context("we don't have an entry for the region") {
      fixture {
        Fixture(
          ContainerAttributesResolver(defaults, clouddriverService, springEnv),
          baseSpec.copy(locations = SimpleLocations(
            account = account,
            regions = setOf(SimpleRegionSpec("south"))
          ))
        )
      }

      test("we only add account") {
        val resolvedSouth = resolved.spec.resolveContainerAttributes("south")
        expect {
          that(resolvedSouth[accountKey]).isEqualTo(awsAccountId)
          that(resolvedSouth[subnetKey]).isNull()
        }
      }
    }
  }

  private fun validateKeysSet(resource: Resource<TitusClusterSpec>) {
    val resolvedEast = resource.spec.resolveContainerAttributes("east")
    val resolvedWest = resource.spec.resolveContainerAttributes("west")
    expect {
      that(resolvedEast[accountKey]).isEqualTo(awsAccountId)
      that(resolvedEast[subnetKey]).isEqualTo(eastSubnets)
      that(resolvedWest[accountKey]).isEqualTo(awsAccountId)
      that(resolvedWest[subnetKey]).isEqualTo(westSubnets)
    }
  }
}
