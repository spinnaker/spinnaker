package com.netflix.spinnaker.keel.ec2.vetos

import com.netflix.spinnaker.keel.api.Locatable
import com.netflix.spinnaker.keel.api.Moniker
import com.netflix.spinnaker.keel.api.Resource
import com.netflix.spinnaker.keel.api.ResourceKind
import com.netflix.spinnaker.keel.api.ResourceSpec
import com.netflix.spinnaker.keel.api.SimpleLocations
import com.netflix.spinnaker.keel.api.SimpleRegionSpec
import com.netflix.spinnaker.keel.api.SubnetAwareLocations
import com.netflix.spinnaker.keel.api.SubnetAwareRegionSpec
import com.netflix.spinnaker.keel.api.ec2.ClassicLoadBalancerHealthCheck
import com.netflix.spinnaker.keel.api.ec2.ClassicLoadBalancerSpec
import com.netflix.spinnaker.keel.api.ec2.ClusterDependencies
import com.netflix.spinnaker.keel.api.ec2.ClusterSpec
import com.netflix.spinnaker.keel.api.ec2.ClusterSpec.ServerGroupSpec
import com.netflix.spinnaker.keel.api.ec2.LoadBalancerDependencies
import com.netflix.spinnaker.keel.api.ec2.SecurityGroupSpec
import com.netflix.spinnaker.keel.clouddriver.CloudDriverCache
import com.netflix.spinnaker.keel.clouddriver.CloudDriverService
import com.netflix.spinnaker.keel.clouddriver.model.SecurityGroupModel
import com.netflix.spinnaker.keel.clouddriver.model.Subnet
import com.netflix.spinnaker.keel.core.api.DEFAULT_SERVICE_ACCOUNT
import com.netflix.spinnaker.keel.core.api.randomUID
import com.netflix.spinnaker.keel.ec2.CLOUD_PROVIDER
import com.netflix.spinnaker.keel.ec2.EC2_CLUSTER_V1
import com.netflix.spinnaker.keel.ec2.EC2_SECURITY_GROUP_V1
import com.netflix.spinnaker.keel.retrofit.RETROFIT_NOT_FOUND
import com.netflix.spinnaker.keel.test.randomString
import com.netflix.spinnaker.keel.test.resource
import com.netflix.spinnaker.keel.veto.VetoResponse
import dev.minutest.junit.JUnit5Minutests
import dev.minutest.rootContext
import io.mockk.Called
import io.mockk.MockKAnswerScope
import io.mockk.coEvery as every
import io.mockk.coVerify as verify
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import strikt.api.expectThat
import strikt.assertions.contains
import strikt.assertions.isFalse
import strikt.assertions.isNotNull
import strikt.assertions.isNullOrEmpty
import strikt.assertions.isTrue

internal class RequiredSecurityGroupVetoTests : JUnit5Minutests {

  data class Fixture<out SPEC : ResourceSpec>(
    val resourceKind: ResourceKind,
    val resourceSpec: SPEC,
    val cloudDriver: CloudDriverService = mockk(),
    val cloudDriverCache: CloudDriverCache = mockk()
  ) {
    val resource: Resource<SPEC>
      get() = resource(resourceKind, resourceSpec)

    val subnet = "internal (vpc0)"
    val vpcId = "vpc-5318008"

    val veto = RequiredSecurityGroupVeto(cloudDriver, cloudDriverCache)
    lateinit var vetoResponse: VetoResponse

    fun check() {
      vetoResponse = runBlocking { veto.check(resource) }
    }
  }

  fun tests() = rootContext {
    derivedContext<Fixture<SecurityGroupSpec>>("a resource that cannot have security group dependencies") {
      fixture {
        Fixture(
          EC2_SECURITY_GROUP_V1.kind,
          SecurityGroupSpec(
            moniker = Moniker(app = "fnord"),
            locations = SimpleLocations(
              account = "prod",
              regions = setOf(
                SimpleRegionSpec("ap-south-1"),
                SimpleRegionSpec("af-south-1")
              )
            ),
            description = "a resource with no dependencies"
          ))
      }

      before {
        check()
      }

      test("the resource is not vetoed") {
        expectThat(vetoResponse.allowed).isTrue()
      }

      test("no call is made to CloudDriver") {
        verify { cloudDriver wasNot Called }
      }
    }

    derivedContext<Fixture<ClassicLoadBalancerSpec>>("a resource that can have security group dependencies") {
      fixture {
        Fixture(
          EC2_SECURITY_GROUP_V1.kind,
          ClassicLoadBalancerSpec(
            moniker = Moniker(app = "fnord"),
            locations = SubnetAwareLocations(
              account = "prod",
              regions = setOf(
                SubnetAwareRegionSpec("ap-south-1"),
                SubnetAwareRegionSpec("af-south-1")
              ),
              subnet = "vpc0"
            ),
            healthCheck = ClassicLoadBalancerHealthCheck("/health")
          )
        )
      }

      before {
        stubSubnets()
      }

      context("…but does not have any") {
        before {
          check()
        }

        test("the resource is not vetoed") {
          expectThat(vetoResponse.allowed).isTrue()
        }

        test("no call is made to CloudDriver") {
          verify { cloudDriver wasNot Called }
        }

        test("there is no message from the veto") {
          expectThat(vetoResponse.message).isNullOrEmpty()
        }
      }

      context("…and does have some") {
        deriveFixture {
          copy(
            resourceSpec = resourceSpec.copy(
              dependencies = LoadBalancerDependencies(setOf("fnord-elb", "fnord-ext"))
            ),
            cloudDriver = cloudDriver,
            cloudDriverCache = cloudDriverCache
          )
        }

        context("the dependencies exist") {
          before {
            stubSecurityGroups(resourceSpec.dependencies.securityGroupNames)
            check()
          }

          test("the resource is not vetoed") {
            expectThat(vetoResponse.allowed).isTrue()
          }

          test("there is no message from the veto") {
            expectThat(vetoResponse.message).isNullOrEmpty()
          }
        }

        context("the dependencies do not exist") {
          before {
            stubSecurityGroups(emptyList())
            check()
          }

          test("the resource is vetoed") {
            expectThat(vetoResponse.allowed).isFalse()
          }

          test("the veto message specifies the missing resources") {
            val sgNames = resourceSpec.dependencies.securityGroupNames
            val missingRegions = resourceSpec.locations.regions.map { it.name }
            expectThat(vetoResponse.message)
              .isNotNull()
              .and {
                sgNames.forEach { sg ->
                  contains("Security group $sg is not found in ${missingRegions.joinToString()}")
                }
              }
          }
        }

        context("the dependencies are missing in some regions") {
          before {
            stubSecurityGroups(
              resourceSpec.dependencies.securityGroupNames,
              resourceSpec.locations.regions.take(1).map(SubnetAwareRegionSpec::name)
            )
            check()
          }

          test("the resource is vetoed") {
            expectThat(vetoResponse.allowed).isFalse()
          }

          test("the veto message specifies the missing resources") {
            val sgNames = resourceSpec.dependencies.securityGroupNames
            val missingRegions = resourceSpec.locations.regions.drop(1).map { it.name }
            expectThat(vetoResponse.message)
              .isNotNull()
              .and {
                sgNames.forEach { sg ->
                  contains("Security group $sg is not found in ${missingRegions.joinToString()}")
                }
              }
          }
        }
      }
    }

    derivedContext<Fixture<ClusterSpec>>("a resource with complex dependency overrides") {
      fixture {
        Fixture(
          EC2_CLUSTER_V1.kind,
          ClusterSpec(
            moniker = Moniker("fnord", "dev"),
            locations = SubnetAwareLocations(
              account = "prod",
              regions = setOf(
                SubnetAwareRegionSpec("ap-south-1"),
                SubnetAwareRegionSpec("af-south-1")
              ),
              subnet = "vpc0"
            ),
            _defaults = ServerGroupSpec(
              dependencies = ClusterDependencies(
                securityGroupNames = setOf("sg-both-regions")
              )
            ),
            overrides = mapOf(
              "ap-south-1" to ServerGroupSpec(
                dependencies = ClusterDependencies(
                  securityGroupNames = setOf(
                    "sg-ap-south-1-only",
                    "sg-both-regions-via-override"
                  )
                )
              ),
              "af-south-1" to ServerGroupSpec(
                dependencies = ClusterDependencies(
                  securityGroupNames = setOf(
                    "sg-af-south-1-only",
                    "sg-both-regions-via-override"
                  )
                )
              )
            )
          )
        )
      }

      before {
        stubSubnets()
        stubSecurityGroups(resourceSpec.allSecurityGroupNames)
        check()
      }

      test("we look for common security groups in all regions") {
        setOf("sg-both-regions", "sg-both-regions-via-override").forEach { sgName ->
          resourceSpec.locations.regions.map(SubnetAwareRegionSpec::name).forEach { region ->
            verify(exactly = 1) {
              cloudDriver.getSecurityGroup(
                user = any(),
                account = resourceSpec.locations.account,
                type = CLOUD_PROVIDER,
                securityGroupName = sgName,
                region = region,
                vpcId = vpcId
              )
            }
          }
        }
      }

      test("we look for override security groups in required regions") {
        verify(exactly = 1) {
          cloudDriver.getSecurityGroup(
            user = any(),
            account = resourceSpec.locations.account,
            type = CLOUD_PROVIDER,
            securityGroupName = "sg-ap-south-1-only",
            region = "ap-south-1",
            vpcId = vpcId
          )
        }
        verify(exactly = 1) {
          cloudDriver.getSecurityGroup(
            user = any(),
            account = resourceSpec.locations.account,
            type = CLOUD_PROVIDER,
            securityGroupName = "sg-af-south-1-only",
            region = "af-south-1",
            vpcId = vpcId
          )
        }
      }

      test("we do not look for override security groups in regions where they are not required") {
        verify(exactly = 0) {
          cloudDriver.getSecurityGroup(
            user = any(),
            account = any(),
            type = any(),
            securityGroupName = "sg-ap-south-1-only",
            region = "af-south-1",
            vpcId = any()
          )
        }
        verify(exactly = 0) {
          cloudDriver.getSecurityGroup(
            user = any(),
            account = any(),
            type = any(),
            securityGroupName = "sg-af-south-1-only",
            region = "ap-south-1",
            vpcId = any()
          )
        }
      }
    }
  }

  /**
   * Sets up a stub for [CloudDriverService.getSecurityGroup] for all security group names in
   * [securityGroupNames] in every region of the fixture.
   */
  private fun Fixture<Locatable<SubnetAwareLocations>>.stubSecurityGroups(
    securityGroupNames: Collection<String>,
    regions: Collection<String> = this.resourceSpec.locations.regions.map(SubnetAwareRegionSpec::name)
  ) {
    every {
      cloudDriver.getSecurityGroup(
        user = DEFAULT_SERVICE_ACCOUNT,
        account = resourceSpec.locations.account,
        type = CLOUD_PROVIDER,
        securityGroupName = match { it in securityGroupNames },
        region = match {
          it in resourceSpec.locations.regions.map(SubnetAwareRegionSpec::name)
        },
        vpcId = vpcId
      )
    } answers {
      if (fourthArg() in securityGroupNames && fifthArg() in regions) {
        SecurityGroupModel(
          type = thirdArg(),
          id = "sg-${randomUID()}",
          name = fourthArg(),
          description = "",
          accountName = secondArg(),
          region = fifthArg(),
          vpcId = sixthArg(),
          moniker = Moniker("fnord", "elb") // TODO: parse from name
        )
      } else {
        throw RETROFIT_NOT_FOUND
      }
    }
  }

  /**
   * Sets up a stub for [CloudDriverCache.networkBy] for all subnets used by the fixture.
   */
  private fun Fixture<Locatable<SubnetAwareLocations>>.stubSubnets() {
    every {
      cloudDriverCache.subnetBy(
        resourceSpec.locations.account,
        match { it in resourceSpec.locations.regions.map(SubnetAwareRegionSpec::name) },
        resourceSpec.locations.subnet!!
      )
    } answers {
      Subnet(randomString(8), vpcId, firstArg(), secondArg(), "${secondArg<String>()}a", thirdArg())
    }
  }

  /**
   * All the security group names used by a [ClusterSpec] regardless of region.
   */
  val ClusterSpec.allSecurityGroupNames: Set<String>
    get() = (defaults.dependencies?.securityGroupNames ?: emptySet()) +
      overrides.values.flatMap {
        it.dependencies?.securityGroupNames ?: emptySet()
      }

  inline fun <reified R> MockKAnswerScope<*, *>.fourthArg() = invocation.args[3] as R
  inline fun <reified R> MockKAnswerScope<*, *>.fifthArg() = invocation.args[4] as R
  inline fun <reified R> MockKAnswerScope<*, *>.sixthArg() = invocation.args[5] as R
}
