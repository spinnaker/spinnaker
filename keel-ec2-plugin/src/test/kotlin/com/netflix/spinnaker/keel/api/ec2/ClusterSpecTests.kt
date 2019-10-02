package com.netflix.spinnaker.keel.api.ec2

import com.fasterxml.jackson.module.kotlin.readValue
import com.netflix.spinnaker.keel.api.ArtifactType.DEB
import com.netflix.spinnaker.keel.api.DeliveryArtifact
import com.netflix.spinnaker.keel.api.ec2.ClusterSpec.ClusterRegion
import com.netflix.spinnaker.keel.api.ec2.ClusterSpec.HealthSpec
import com.netflix.spinnaker.keel.api.ec2.ClusterSpec.LaunchConfigurationSpec
import com.netflix.spinnaker.keel.api.ec2.ClusterSpec.Locations
import com.netflix.spinnaker.keel.api.ec2.ClusterSpec.ServerGroupSpec
import com.netflix.spinnaker.keel.api.ec2.HealthCheckType.ELB
import com.netflix.spinnaker.keel.clouddriver.CloudDriverService
import com.netflix.spinnaker.keel.clouddriver.MemoryCloudDriverCache
import com.netflix.spinnaker.keel.clouddriver.model.Network
import com.netflix.spinnaker.keel.clouddriver.model.Subnet
import com.netflix.spinnaker.keel.ec2.CLOUD_PROVIDER
import com.netflix.spinnaker.keel.ec2.resource.ResolvedImages
import com.netflix.spinnaker.keel.model.Moniker
import com.netflix.spinnaker.keel.serialization.configuredObjectMapper
import com.netflix.spinnaker.keel.serialization.configuredYamlMapper
import dev.minutest.TestContextBuilder
import dev.minutest.junit.JUnit5Minutests
import dev.minutest.rootContext
import io.mockk.coEvery
import io.mockk.mockk
import org.apache.commons.lang3.RandomStringUtils
import strikt.api.expect
import strikt.api.expectThat
import strikt.assertions.all
import strikt.assertions.contains
import strikt.assertions.containsExactlyInAnyOrder
import strikt.assertions.hasSize
import strikt.assertions.isEqualTo
import strikt.assertions.propertiesAreEqualTo
import java.time.Duration

internal class ClusterSpecTests : JUnit5Minutests {

  fun tests() = rootContext<ClusterSpec> {
    context("a spec with multiple regions and minimal configuration") {
      fixture {
        ClusterSpec(
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
                availabilityZones = setOf("us-east-1c", "us-east-1d", "us-east-1e")
              ),
              ClusterRegion(
                region = "us-west-2",
                subnet = "internal (vpc0)",
                availabilityZones = setOf("us-west-2a", "us-west-2b", "us-west-2c")
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
                keyPair = "fnord-keypair-325719997469-us-west-2"
              )
            )
          )
        )
      }

      mapOf(
        "YAML" to configuredYamlMapper(),
        "JSON" to configuredObjectMapper()
      ).forEach { (format, mapper) ->
        test("can serialize and deserialize as $format") {
          val text = mapper.writeValueAsString(this)

          println(text)

          val tree = mapper.readValue<ClusterSpec>(text)
          expectThat(tree).propertiesAreEqualTo(this)
        }
      }

      resolvedContext {
        test("there is one server group per region specified in the cluster") {
          expectThat(result).hasSize(spec.locations.regions.size)
        }

        test("image resolution is applied to all server groups") {
          expect {
            that(result).all {
              get { launchConfiguration.appVersion }
                .isEqualTo(appVersion)
            }
            that(usEastServerGroup.launchConfiguration.imageId).isEqualTo(usEastImageId)
            that(usWestServerGroup.launchConfiguration.imageId).isEqualTo(usWestImageId)
          }
        }

        test("configuration is applied from the cluster-wide defaults when not overridden in region") {
          expectThat(result)
            .all {
              get { launchConfiguration.instanceType }
                .isEqualTo(spec.defaults.launchConfiguration?.instanceType)
            }
        }

        test("configuration is applied from the region when not present in the cluster-wide defaults") {
          expectThat(usWestServerGroup)
            .get { launchConfiguration.keyPair }
            .isEqualTo(spec.overrides["us-west-2"]?.launchConfiguration?.keyPair)
        }

        test("cluster-wide default configuration can be overridden by regions") {
          expect {
            that(usEastServerGroup.launchConfiguration.iamRole)
              .isEqualTo(spec.overrides["us-east-1"]?.launchConfiguration?.iamRole)
            that(usWestServerGroup.launchConfiguration.iamRole)
              .isEqualTo(spec.defaults.launchConfiguration?.iamRole)
          }
        }

        test("override dependencies are merged with cluster-wide defaults") {
          expect {
            that(usEastServerGroup.dependencies.loadBalancerNames)
              .contains(spec.defaults.dependencies?.loadBalancerNames!!)
              .contains(spec.overrides["us-east-1"]?.dependencies?.loadBalancerNames!!)
            that(usEastServerGroup.dependencies.securityGroupNames)
              .contains(spec.defaults.dependencies?.securityGroupNames!!)
              .contains(spec.overrides["us-east-1"]?.dependencies?.securityGroupNames!!)
          }
        }

        test("override health settings are merged with cluster-wide defaults") {
          expect {
            that(usEastServerGroup.health.warmup)
              .isEqualTo(spec.defaults.health?.warmup)
            that(usEastServerGroup.health.healthCheckType)
              .isEqualTo(spec.overrides["us-east-1"]?.health?.healthCheckType)
          }
        }
      }

      context("availability zones are not specified explicitly") {
        deriveFixture {
          copy(
            locations = locations.copy(
              regions = locations.regions.map { it.copy(availabilityZones = null) }.toSet()
            )
          )
        }

        resolvedContext {
          fun randomHex(): String = RandomStringUtils.random(8, "0123456789abcdef")

          test("all availability zones are assigned") {
            val vpcEast = Network(CLOUD_PROVIDER, "vpc-${randomHex()}", "vpc0", spec.locations.accountName, "us-east-1")
            val vpcWest = Network(CLOUD_PROVIDER, "vpc-${randomHex()}", "vpc0", spec.locations.accountName, "us-west-2")
            val usEastSubnets = setOf("c", "d", "e").map {
              Subnet(
                "subnet-${randomHex()}",
                vpcEast.id,
                spec.locations.accountName,
                "us-east-1",
                "us-east-1$it",
                "internal (vpc0)"
              )
            }
            val usWestSubnets = setOf("a", "b", "c").map {
              Subnet(
                "subnet-${randomHex()}",
                vpcWest.id,
                spec.locations.accountName,
                "us-west-2",
                "us-west-2$it",
                "internal (vpc0)"
              )
            }
            val subnets = (usEastSubnets + usWestSubnets).toSet()

            coEvery { cloudDriverService.listSubnets("aws") } returns subnets

            expect {
              that(usEastServerGroup.location.availabilityZones)
                .containsExactlyInAnyOrder("us-east-1c", "us-east-1d", "us-east-1e")
              that(usWestServerGroup.location.availabilityZones)
                .containsExactlyInAnyOrder("us-west-2a", "us-west-2b", "us-west-2c")
            }
          }
        }
      }
    }
  }
}

private data class ResolvedSpecFixture(
  val spec: ClusterSpec
) {
  val usEastServerGroup
    get() = result.first { it.location.region == "us-east-1" }
  val usWestServerGroup
    get() = result.first { it.location.region == "us-west-2" }

  val appVersion = "fnord-1.0.0"
  val usEastImageId = "ami-6874986"
  val usWestImageId = "ami-6271051"

  val cloudDriverService = mockk<CloudDriverService>()
  private val cloudDriverCache = MemoryCloudDriverCache(cloudDriverService)

  fun resolve(): Set<ServerGroup> = spec.resolve(
    ResolvedImages(
      appVersion,
      mapOf(
        "us-east-1" to usEastImageId,
        "us-west-2" to usWestImageId
      )
    ),
    cloudDriverCache
  )

  val result: Set<ServerGroup> by lazy { resolve() }
}

private fun TestContextBuilder<*, ClusterSpec>.resolvedContext(
  builder: TestContextBuilder<ClusterSpec, ResolvedSpecFixture>.() -> Unit
) =
  derivedContext<ResolvedSpecFixture>("when resolved") {
    deriveFixture {
      ResolvedSpecFixture(this)
    }

    builder()
  }
