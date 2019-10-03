package com.netflix.spinnaker.keel.ec2.normalizers

import com.netflix.spinnaker.keel.api.ArtifactType.DEB
import com.netflix.spinnaker.keel.api.DeliveryArtifact
import com.netflix.spinnaker.keel.api.SPINNAKER_API_V1
import com.netflix.spinnaker.keel.api.ec2.ArtifactImageProvider
import com.netflix.spinnaker.keel.api.ec2.Capacity
import com.netflix.spinnaker.keel.api.ec2.ClusterSpec
import com.netflix.spinnaker.keel.api.ec2.ClusterSpec.ClusterRegion
import com.netflix.spinnaker.keel.api.ec2.ClusterSpec.HealthSpec
import com.netflix.spinnaker.keel.api.ec2.ClusterSpec.LaunchConfigurationSpec
import com.netflix.spinnaker.keel.api.ec2.ClusterSpec.Locations
import com.netflix.spinnaker.keel.api.ec2.ClusterSpec.ServerGroupSpec
import com.netflix.spinnaker.keel.api.ec2.Dependencies
import com.netflix.spinnaker.keel.api.ec2.HealthCheckType.ELB
import com.netflix.spinnaker.keel.api.ec2.get
import com.netflix.spinnaker.keel.clouddriver.CloudDriverService
import com.netflix.spinnaker.keel.clouddriver.MemoryCloudDriverCache
import com.netflix.spinnaker.keel.clouddriver.model.Network
import com.netflix.spinnaker.keel.clouddriver.model.Subnet
import com.netflix.spinnaker.keel.ec2.CLOUD_PROVIDER
import com.netflix.spinnaker.keel.model.Moniker
import com.netflix.spinnaker.keel.test.resource
import dev.minutest.junit.JUnit5Minutests
import dev.minutest.rootContext
import io.mockk.coEvery
import io.mockk.mockk
import org.apache.commons.lang3.RandomStringUtils
import org.apache.commons.lang3.RandomStringUtils.randomNumeric
import strikt.api.expect
import strikt.api.expectThat
import strikt.assertions.isEqualTo
import java.time.Duration

internal class ClusterAvailabilityZonesNormalizerTests : JUnit5Minutests {

  data class Fixture(
    val eastAvailabilityZones: Set<String>?,
    val westAvailabilityZones: Set<String>?
  ) {
    val cloudDriverService = mockk<CloudDriverService>()

    val subject = ClusterAvailabilityZonesNormalizer(
      MemoryCloudDriverCache(cloudDriverService)
    )

    val resource = resource(
      apiVersion = SPINNAKER_API_V1.subApi("ec2"),
      kind = "cluster",
      spec = ClusterSpec(
        moniker = Moniker(
          app = "fnord",
          stack = "test"
        ),
        imageProvider = ArtifactImageProvider(DeliveryArtifact("fnord", DEB)),
        locations = Locations(
          accountName = "test",
          regions = setOf(
            ClusterRegion(
              region = "us-east-1",
              subnet = "internal (vpc0)",
              availabilityZones = eastAvailabilityZones ?: emptySet()
            ),
            ClusterRegion(
              region = "us-west-2",
              subnet = "internal (vpc0)",
              availabilityZones = westAvailabilityZones ?: emptySet()
            )
          )
        ),
        _defaults = ServerGroupSpec(
          launchConfiguration = LaunchConfigurationSpec(
            instanceType = "m5.large",
            ebsOptimized = true,
            iamRole = "fnordInstanceProfile",
            instanceMonitoring = false
          ),
          capacity = Capacity(2, 2, 2),
          dependencies = Dependencies(
            loadBalancerNames = setOf("fnord-internal"),
            securityGroupNames = setOf("fnord", "fnord-elb")
          ),
          health = HealthSpec(
            warmup = Duration.ofSeconds(120)
          )
        ),
        overrides = mapOf(
          "us-east-1" to ServerGroupSpec(
            launchConfiguration = LaunchConfigurationSpec(
              iamRole = "fnordEastInstanceProfile",
              keyPair = "fnord-keypair-325719997469-us-east-1"
            ),
            capacity = Capacity(5, 5, 5),
            dependencies = Dependencies(
              loadBalancerNames = setOf("fnord-external"),
              securityGroupNames = setOf("fnord-ext")
            ),
            health = HealthSpec(
              healthCheckType = ELB
            )
          ),
          "us-west-2" to ServerGroupSpec(
            launchConfiguration = LaunchConfigurationSpec(
              keyPair = "fnord-keypair-${randomNumeric(12)}-us-west-2"
            )
          )
        )
      )
    )

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

  fun tests() = rootContext<Fixture>() {
    context("no availability zones are specified explicitly") {
      fixture {
        Fixture(
          eastAvailabilityZones = null,
          westAvailabilityZones = null
        )
      }

      before {
        coEvery { cloudDriverService.listSubnets("aws") } returns subnets
      }

      test("availability zones in all regions are assigned") {
        val normalized = subject.normalize(resource)

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
        Fixture(
          eastAvailabilityZones = setOf("us-east-1c"),
          westAvailabilityZones = null
        )
      }

      before {
        coEvery { cloudDriverService.listSubnets("aws") } returns subnets
      }

      test("specified availability zones are left alone") {
        val normalized = subject.normalize(resource)

        expectThat(normalized.spec)
          .get { locations["us-east-1"].availabilityZones }
          .isEqualTo(resource.spec.locations["us-east-1"].availabilityZones)
      }

      test("unspecified availability zones are assigned") {
        val normalized = subject.normalize(resource)

        expectThat(normalized.spec)
          .get { locations["us-west-2"].availabilityZones }
          .isEqualTo(usWestAvailabilityZones)
      }
    }

    context("all availability zones are specified explicitly") {
      fixture {
        Fixture(
          eastAvailabilityZones = setOf("us-east-1c"),
          westAvailabilityZones = setOf("us-west-2c")
        )
      }

      before {
        coEvery { cloudDriverService.listSubnets("aws") } returns subnets
      }

      test("no changes are made") {
        val normalized = subject.normalize(resource)

        expectThat(normalized.spec)
          .isEqualTo(resource.spec)
      }
    }
  }
}
