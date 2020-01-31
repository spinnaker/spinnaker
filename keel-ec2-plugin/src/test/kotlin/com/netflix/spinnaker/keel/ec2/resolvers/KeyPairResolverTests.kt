package com.netflix.spinnaker.keel.ec2.resolvers

import com.netflix.spinnaker.keel.api.Moniker
import com.netflix.spinnaker.keel.api.SubnetAwareLocations
import com.netflix.spinnaker.keel.api.SubnetAwareRegionSpec
import com.netflix.spinnaker.keel.api.ec2.ClusterSpec
import com.netflix.spinnaker.keel.clouddriver.CloudDriverCache
import com.netflix.spinnaker.keel.clouddriver.model.Network
import com.netflix.spinnaker.keel.ec2.CLOUD_PROVIDER
import com.netflix.spinnaker.keel.ec2.SPINNAKER_EC2_API_V1
import com.netflix.spinnaker.keel.plugin.supporting
import com.netflix.spinnaker.keel.test.resource
import dev.minutest.junit.JUnit5Minutests
import dev.minutest.rootContext
import io.mockk.every
import io.mockk.mockk
import strikt.api.expectThat
import strikt.assertions.containsExactly
import strikt.assertions.isEqualTo

internal class KeyPairResolverTests : JUnit5Minutests {
  val vpcWest = Network(CLOUD_PROVIDER, "vpc-1452353", "vpc0", "test", "us-west-2")
  val vpcEast = Network(CLOUD_PROVIDER, "vpc-4342589", "vpc0", "test", "us-east-1")
  val baseSpec = ClusterSpec(
    moniker = Moniker(app = "keel", stack = "test"),
    locations = SubnetAwareLocations(
      account = vpcWest.account,
      vpc = "vpc0",
      subnet = "internal (vpc0)",
      regions = listOf(vpcWest, vpcEast).map { subnet ->
        SubnetAwareRegionSpec(
          name = subnet.region,
          availabilityZones = listOf("a", "b", "c").map { "${subnet.region}$it" }.toSet()
        )
      }.toSet()
    ),
    _defaults = ClusterSpec.ServerGroupSpec(
      launchConfiguration = ClusterSpec.LaunchConfigurationSpec(
        keyPair = "nf-keypair-test-fake"
      )
    )
  )

  val cloudDriverCache = mockk<CloudDriverCache>()

  data class Fixture(val subject: KeyPairResolver, val spec: ClusterSpec) {
    val resource = resource(
      apiVersion = SPINNAKER_EC2_API_V1,
      kind = "cluster",
      spec = spec
    )
    val resolved by lazy { subject(resource) }
  }

  fun tests() = rootContext<Fixture> {
    fixture {
      Fixture(
        KeyPairResolver(cloudDriverCache),
        baseSpec
      )
    }

    test("supports the resource kind") {
      expectThat(listOf(subject).supporting(resource))
        .containsExactly(subject)
    }

    context("non-templated default key pair configured in clouddriver") {
      before {
        with(cloudDriverCache) {
          every { defaultKeyPairForAccount("test") } returns "nf-test-keypair-a"
        }
      }

      context("key pair specified in the spec defaults") {
        fixture {
          Fixture(
            KeyPairResolver(cloudDriverCache),
            baseSpec
          )
        }

        test("default is not touched in the spec") {
          expectThat(resolved.spec.defaults.launchConfiguration!!.keyPair)
            .isEqualTo(baseSpec.defaults.launchConfiguration!!.keyPair)
        }
      }

      context("no launch config in the spec defaults") {
        fixture {
          Fixture(
            KeyPairResolver(cloudDriverCache),
            baseSpec.withNoDefaultLaunchConfig()
          )
        }

        test("default is resolved in the spec") {
          expectThat(resolved.spec.defaults.launchConfiguration!!.keyPair)
            .isEqualTo("nf-test-keypair-a")
        }
      }
      context("no key pair in the spec defaults") {
        fixture {
          Fixture(
            KeyPairResolver(cloudDriverCache),
            baseSpec.withNoDefaultKeyPair()
          )
        }

        test("default is resolved in the spec") {
          expectThat(resolved.spec.defaults.launchConfiguration!!.keyPair)
            .isEqualTo("nf-test-keypair-a")
        }
      }
    }

    context("templated default key pair configured in clouddriver") {
      before {
        with(cloudDriverCache) {
          every { defaultKeyPairForAccount("test") } returns "nf-keypair-test-{{region}}"
        }
      }

      context("no launch configuration overrides in the spec") {
        fixture {
          Fixture(
            KeyPairResolver(cloudDriverCache),
            baseSpec
          )
        }

        test("key pair overrides are resolved in the spec") {
          expectThat(resolved.spec.overrides.size)
            .isEqualTo(2)
          expectThat(resolved.spec.overrides["us-west-2"]!!.launchConfiguration!!.keyPair)
            .isEqualTo("nf-keypair-test-us-west-2")
          expectThat(resolved.spec.overrides["us-east-1"]!!.launchConfiguration!!.keyPair)
            .isEqualTo("nf-keypair-test-us-east-1")
        }
      }

      context("some launch configuration overrides present in the spec") {
        fixture {
          Fixture(
            KeyPairResolver(cloudDriverCache),
            baseSpec.withKeyPairOverride("us-west-2")
          )
        }

        test("only missing key pair overrides are resolved in the spec") {
          expectThat(resolved.spec.overrides.size)
            .isEqualTo(2)
          expectThat(resolved.spec.overrides["us-west-2"]!!.launchConfiguration!!.keyPair)
            .isEqualTo("foobar")
          expectThat(resolved.spec.overrides["us-east-1"]!!.launchConfiguration!!.keyPair)
            .isEqualTo("nf-keypair-test-us-east-1")
        }
      }
    }
  }

  private fun ClusterSpec.withNoDefaultLaunchConfig() =
    copy(
      _defaults = defaults.copy(
        launchConfiguration = null
      )
    )

  private fun ClusterSpec.withNoDefaultKeyPair() =
    copy(
      _defaults = defaults.copy(
        launchConfiguration = defaults.launchConfiguration!!.copy(
          keyPair = null
        )
      )
    )

  private fun ClusterSpec.withKeyPairOverride(region: String) =
    copy(
      overrides = mapOf(
        region to ClusterSpec.ServerGroupSpec(
          launchConfiguration = ClusterSpec.LaunchConfigurationSpec(
            keyPair = "foobar"
          )
        )
      )
    )
}
