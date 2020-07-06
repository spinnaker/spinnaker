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
import com.netflix.spinnaker.keel.api.ec2.ClusterDependencies
import com.netflix.spinnaker.keel.api.ec2.ClusterSpec
import com.netflix.spinnaker.keel.api.ec2.ClusterSpec.ServerGroupSpec
import com.netflix.spinnaker.keel.api.ec2.SecurityGroupSpec
import com.netflix.spinnaker.keel.clouddriver.CloudDriverCache
import com.netflix.spinnaker.keel.clouddriver.CloudDriverService
import com.netflix.spinnaker.keel.clouddriver.model.ApplicationLoadBalancerModel
import com.netflix.spinnaker.keel.clouddriver.model.ApplicationLoadBalancerModel.TargetGroup
import com.netflix.spinnaker.keel.clouddriver.model.ApplicationLoadBalancerModel.TargetGroupAttributes
import com.netflix.spinnaker.keel.clouddriver.model.ApplicationLoadBalancerModel.TargetGroupMatcher
import com.netflix.spinnaker.keel.clouddriver.model.ClassicLoadBalancerModel
import com.netflix.spinnaker.keel.clouddriver.model.ClassicLoadBalancerModel.ClassicLoadBalancerHealthCheck
import com.netflix.spinnaker.keel.clouddriver.model.Network
import com.netflix.spinnaker.keel.core.api.DEFAULT_SERVICE_ACCOUNT
import com.netflix.spinnaker.keel.ec2.EC2_SECURITY_GROUP_V1
import com.netflix.spinnaker.keel.test.resource
import com.netflix.spinnaker.keel.veto.VetoResponse
import dev.minutest.junit.JUnit5Minutests
import dev.minutest.rootContext
import io.mockk.Called
import io.mockk.coEvery as every
import io.mockk.coVerify as verify
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import strikt.api.Assertion
import strikt.api.expectThat
import strikt.assertions.contains
import strikt.assertions.isNotNull
import strikt.assertions.isNullOrEmpty

internal class RequiredLoadBalancerVetoTests : JUnit5Minutests {

  data class Fixture<out SPEC : ResourceSpec>(
    val resourceKind: ResourceKind,
    val resourceSpec: SPEC,
    val cloudDriver: CloudDriverService = mockk(),
    val cloudDriverCache: CloudDriverCache = mockk()
  ) {
    val resource: Resource<SPEC>
      get() = resource(resourceKind, resourceSpec)

    val vpcIds = mapOf(
      ("prod" to "ap-south-1") to "vpc-1111111",
      ("prod" to "af-south-1") to "vpc-2222222",
      ("test" to "ap-south-1") to "vpc-3333333",
      ("test" to "af-south-1") to "vpc-4444444"
    )

    private val veto = RequiredLoadBalancerVeto(cloudDriver, cloudDriverCache)

    private lateinit var vetoResponse: VetoResponse

    fun check() {
      vetoResponse = runBlocking { veto.check(resource) }
    }

    val response: Assertion.Builder<VetoResponse>
      get() = expectThat(vetoResponse)
  }

  fun tests() = rootContext {
    derivedContext<Fixture<SecurityGroupSpec>>("a resource that cannot have load balancer dependencies") {
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
        response.isAllowed()
      }

      test("no call is made to CloudDriver") {
        verify { cloudDriver wasNot Called }
      }
    }

    derivedContext<Fixture<ClusterSpec>>("a resource that can have load balancer dependencies") {
      fixture {
        Fixture(
          EC2_SECURITY_GROUP_V1.kind,
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
            _defaults = ServerGroupSpec(),
            overrides = mapOf()
          )
        )
      }

      context("…but does not have any") {
        before {
          check()
        }

        test("the resource is not vetoed") {
          response.isAllowed()
        }

        test("no call is made to CloudDriver") {
          verify { cloudDriver wasNot Called }
        }

        test("there is no message from the veto") {
          response.message.isNullOrEmpty()
        }
      }

      context("…and does have some") {
        deriveFixture {
          copy(
            resourceSpec = resourceSpec.copy(
              _defaults = resourceSpec.defaults.copy(
                dependencies = ClusterDependencies(
                  loadBalancerNames = setOf("fnord-ext", "fnord-int"),
                  targetGroups = setOf("fnord-target-group")
                )
              )
            ),
            // mocks need to be copied over otherwise any stubbing is lost
            cloudDriver = cloudDriver,
            cloudDriverCache = cloudDriverCache
          )
        }

        before {
          every { cloudDriverCache.networkBy(any()) } answers {
            val vpcId = firstArg<String>()
            val (account, region) = vpcIds.filterValues { it == vpcId }.keys.first()
            Network("aws", vpcId, null, account, region)
          }
        }

        context("the dependencies exist") {
          before {
            stubLoadBalancers(resourceSpec.allLoadBalancerNames, resourceSpec.allTargetGroupNames)
            check()
          }

          test("the resource is not vetoed") {
            response.isAllowed()
          }

          test("there is no message from the veto") {
            response.message.isNullOrEmpty()
          }
        }

        context("the dependencies do not exist") {
          before {
            stubLoadBalancers(emptyList(), emptyList())
            check()
          }

          test("the resource is vetoed") {
            response.isDenied()
          }

          test("the veto message specifies the missing resources") {
            val lbNames = resourceSpec.allLoadBalancerNames
            val tgNames = resourceSpec.allTargetGroupNames
            val missingRegions = resourceSpec.locations.regions.map { it.name }
            response.message
              .isNotNull()
              .and {
                lbNames.forEach { lb ->
                  contains("Load balancer $lb is not found in ${resourceSpec.locations.account} / ${missingRegions.joinToString()}")
                }
                tgNames.forEach { tg ->
                  contains("Target group $tg is not found in ${resourceSpec.locations.account} / ${missingRegions.joinToString()}")
                }
              }
          }
        }

        context("the dependencies are missing in some regions") {
          before {
            stubLoadBalancers(
              resourceSpec.allLoadBalancerNames,
              resourceSpec.allTargetGroupNames,
              regions = resourceSpec.locations.regions.take(1).map(SubnetAwareRegionSpec::name)
            )
            check()
          }

          test("the resource is vetoed") {
            response.isDenied()
          }

          test("the veto message specifies the missing resources") {
            val lbNames = resourceSpec.allLoadBalancerNames
            val tgNames = resourceSpec.allTargetGroupNames
            val missingRegions = resourceSpec
              .locations
              .regions
              .drop(1)
              .map(SubnetAwareRegionSpec::name)
            response.message
              .isNotNull()
              .and {
                lbNames.forEach { lb ->
                  contains("Load balancer $lb is not found in ${resourceSpec.locations.account} / ${missingRegions.joinToString()}")
                }
                tgNames.forEach { tg ->
                  contains("Target group $tg is not found in ${resourceSpec.locations.account} / ${missingRegions.joinToString()}")
                }
              }
          }
        }

        context("the dependencies exist but in a different account") {
          before {
            stubLoadBalancers(
              resourceSpec.allLoadBalancerNames,
              resourceSpec.allTargetGroupNames,
              account = "test"
            )
            check()
          }

          test("the resource is vetoed") {
            response.isDenied()
          }

          test("the veto message specifies the missing resources") {
            val lbNames = resourceSpec.allLoadBalancerNames
            val tgNames = resourceSpec.allTargetGroupNames
            val allRegions = resourceSpec.locations.regions.map(SubnetAwareRegionSpec::name)
            response.message
              .isNotNull()
              .and {
                lbNames.forEach { lb ->
                  contains("Load balancer $lb is not found in ${resourceSpec.locations.account} / ${allRegions.joinToString()}")
                }
                tgNames.forEach { tg ->
                  contains("Target group $tg is not found in ${resourceSpec.locations.account} / ${allRegions.joinToString()}")
                }
              }
          }
        }
      }
    }
  }

  /**
   * Sets up a stub for [CloudDriverService.getClassicLoadBalancer] for all load balancer names in
   * [loadBalancerNames] in every region of the fixture.
   */
  private fun Fixture<Locatable<SubnetAwareLocations>>.stubLoadBalancers(
    loadBalancerNames: Collection<String>,
    targetGroupNames: Collection<String>,
    account: String = this.resourceSpec.locations.account,
    regions: Collection<String> = this.resourceSpec.locations.regions.map(SubnetAwareRegionSpec::name)
  ) {
    every {
      cloudDriver.loadBalancersForApplication(
        user = DEFAULT_SERVICE_ACCOUNT,
        application = resourceSpec.application
      )
    } answers {
      regions.flatMap { region ->
        loadBalancerNames.map { loadBalancerName ->
          ClassicLoadBalancerModel(
            moniker = Moniker("fnord", "elb"), // TODO: parse from name
            loadBalancerName = loadBalancerName,
            availabilityZones = setOf("a", "b", "c").map { "$region$it" }.toSet(),
            vpcId = vpcIds.getValue(account to region),
            subnets = setOf(),
            scheme = "internal",
            idleTimeout = 0,
            securityGroups = setOf(),
            listenerDescriptions = listOf(),
            healthCheck = ClassicLoadBalancerHealthCheck(
              target = "",
              interval = 0,
              timeout = 0,
              unhealthyThreshold = 0,
              healthyThreshold = 0
            )
          )
        }
      } + regions.map { region ->
        ApplicationLoadBalancerModel(
          moniker = Moniker(resourceSpec.application, "stub", "alb"),
          loadBalancerName = "${resourceSpec.application}-stub-alb",
          targetGroups = targetGroupNames.map { targetGroupName ->
            TargetGroup(
              targetGroupName = targetGroupName,
              loadBalancerNames = emptyList(),
              targetType = "whatever-this-is",
              matcher = TargetGroupMatcher("200"),
              protocol = "https",
              port = 8080,
              healthCheckEnabled = true,
              healthCheckTimeoutSeconds = 30,
              healthCheckPort = "8080",
              healthCheckProtocol = "https",
              healthCheckPath = "/healthcheck",
              healthCheckIntervalSeconds = 60,
              healthyThresholdCount = 10,
              unhealthyThresholdCount = 5,
              vpcId = vpcIds.getValue(account to region),
              attributes = TargetGroupAttributes()
            )
          },
          availabilityZones = setOf("a", "b", "c").map { "$region$it" }.toSet(),
          vpcId = vpcIds.getValue(account to region),
          subnets = emptySet(),
          scheme = "https",
          idleTimeout = 60,
          securityGroups = emptySet(),
          listeners = emptyList(),
          ipAddressType = "v4"
        )
      }
    }
  }

  /**
   * All the load balancer names used by a [ClusterSpec] regardless of region.
   */
  private val ClusterSpec.allLoadBalancerNames: Set<String>
    get() = (defaults.dependencies?.loadBalancerNames ?: emptySet()) +
      overrides.values.flatMap {
        it.dependencies?.loadBalancerNames ?: emptySet()
      }

  /**
   * All the target group names used by a [ClusterSpec] regardless of region.
   */
  private val ClusterSpec.allTargetGroupNames: Set<String>
    get() = (defaults.dependencies?.targetGroups ?: emptySet()) +
      overrides.values.flatMap {
        it.dependencies?.targetGroups ?: emptySet()
      }

  private fun Assertion.Builder<VetoResponse>.isAllowed(): Assertion.Builder<VetoResponse> =
    assert("is allowed") {
      if (it.allowed) {
        pass()
      } else {
        fail(description = it.message)
      }
    }

  private fun Assertion.Builder<VetoResponse>.isDenied(): Assertion.Builder<VetoResponse> =
    assert("is denied") {
      if (it.allowed) {
        fail(description = "is allowed")
      } else {
        pass()
      }
    }

  private val Assertion.Builder<VetoResponse>.message: Assertion.Builder<String?>
    get() = get { message }
}
