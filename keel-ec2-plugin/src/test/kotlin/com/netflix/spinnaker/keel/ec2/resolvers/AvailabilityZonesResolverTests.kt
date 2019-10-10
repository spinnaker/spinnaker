package com.netflix.spinnaker.keel.ec2.resolvers

import com.netflix.spinnaker.keel.api.Locatable
import com.netflix.spinnaker.keel.api.Resource
import com.netflix.spinnaker.keel.api.ec2.get
import com.netflix.spinnaker.keel.clouddriver.CloudDriverService
import com.netflix.spinnaker.keel.clouddriver.model.Network
import com.netflix.spinnaker.keel.clouddriver.model.Subnet
import com.netflix.spinnaker.keel.ec2.CLOUD_PROVIDER
import com.netflix.spinnaker.keel.model.SubnetAwareRegionSpec
import com.netflix.spinnaker.keel.plugin.supporting
import dev.minutest.junit.JUnit5Minutests
import dev.minutest.rootContext
import io.mockk.coEvery
import io.mockk.mockk
import org.apache.commons.lang3.RandomStringUtils
import strikt.api.expect
import strikt.api.expectThat
import strikt.assertions.containsExactly
import strikt.assertions.isEqualTo

internal abstract class AvailabilityZonesResolverTests<T : Locatable<SubnetAwareRegionSpec>> : JUnit5Minutests {

  abstract fun createFixture(
    eastAvailabilityZones: Set<String>?,
    westAvailabilityZones: Set<String>?
  ): Fixture<T>

  abstract class Fixture<T : Locatable<SubnetAwareRegionSpec>>(val resource: Resource<T>) {
    val cloudDriverService = mockk<CloudDriverService>()

    abstract val subject: AvailabilityZonesResolver<T>

    private val vpcEast = Network(CLOUD_PROVIDER, "vpc-${randomHex()}", "vpc0", resource.spec.locations.accountName, "us-east-1")
    private val vpcWest = Network(CLOUD_PROVIDER, "vpc-${randomHex()}", "vpc0", resource.spec.locations.accountName, "us-west-2")
    private val usEastSubnets = setOf("c", "d", "e").map {
      Subnet(
        id = "subnet-${randomHex()}",
        vpcId = vpcEast.id,
        account = resource.spec.locations.accountName,
        region = "us-east-1",
        availabilityZone = "us-east-1$it",
        purpose = "internal (vpc0)"
      )
    }
    private val usWestSubnets = setOf("a", "b", "c").map {
      Subnet(
        id = "subnet-${randomHex()}",
        vpcId = vpcWest.id,
        account = resource.spec.locations.accountName,
        region = "us-west-2",
        availabilityZone = "us-west-2$it",
        purpose = "internal (vpc0)"
      )
    }
    val subnets = (usEastSubnets + usWestSubnets).toSet()

    val usEastAvailabilityZones = usEastSubnets.map { it.availabilityZone }.toSet()
    val usWestAvailabilityZones = usWestSubnets.map { it.availabilityZone }.toSet()

    private fun randomHex(): String = RandomStringUtils.random(8, "0123456789abcdef")
  }

  fun tests() = rootContext<Fixture<T>>() {
    context("no availability zones are specified explicitly") {
      fixture {
        createFixture(
          eastAvailabilityZones = null,
          westAvailabilityZones = null
        )
      }

      before {
        coEvery { cloudDriverService.listSubnets("aws") } returns subnets
      }

      test("resolver supports the spec type") {
        expectThat(listOf(subject).supporting(resource))
          .containsExactly(subject)
      }

      test("availability zones in all regions are assigned") {
        val normalized = subject(resource)

        expect {
          that(normalized.spec)
            .get { locations["us-east-1"].availabilityZones }
            .isEqualTo(usEastAvailabilityZones)

          that(normalized.spec)
            .get { locations["us-west-2"].availabilityZones }
            .isEqualTo(usWestAvailabilityZones)
        }
      }
    }

    context("one region's availability zones are specified explicitly") {
      fixture {
        createFixture(
          eastAvailabilityZones = setOf("us-east-1c"),
          westAvailabilityZones = null
        )
      }

      before {
        coEvery { cloudDriverService.listSubnets("aws") } returns subnets
      }

      test("specified availability zones are left alone") {
        val normalized = subject(resource)

        expectThat(normalized.spec)
          .get { locations["us-east-1"].availabilityZones }
          .isEqualTo(resource.spec.locations["us-east-1"].availabilityZones)
      }

      test("unspecified availability zones are assigned") {
        val normalized = subject(resource)

        expectThat(normalized.spec)
          .get { locations["us-west-2"].availabilityZones }
          .isEqualTo(usWestAvailabilityZones)
      }
    }

    context("all availability zones are specified explicitly") {
      fixture {
        createFixture(
          eastAvailabilityZones = setOf("us-east-1c"),
          westAvailabilityZones = setOf("us-west-2c")
        )
      }

      before {
        coEvery { cloudDriverService.listSubnets("aws") } returns subnets
      }

      test("no changes are made") {
        val normalized = subject(resource)

        expectThat(normalized.spec)
          .isEqualTo(resource.spec)
      }
    }
  }
}
