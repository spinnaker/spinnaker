package com.netflix.spinnaker.keel.ec2.resource

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import com.netflix.spinnaker.keel.api.ec2.ApplicationLoadBalancerSpec
import com.netflix.spinnaker.keel.clouddriver.CloudDriverCache
import com.netflix.spinnaker.keel.clouddriver.CloudDriverService
import com.netflix.spinnaker.keel.clouddriver.model.ApplicationLoadBalancerModel
import com.netflix.spinnaker.keel.clouddriver.model.ApplicationLoadBalancerModel.Action
import com.netflix.spinnaker.keel.clouddriver.model.ApplicationLoadBalancerModel.ApplicationLoadBalancerListener
import com.netflix.spinnaker.keel.clouddriver.model.ApplicationLoadBalancerModel.TargetGroup
import com.netflix.spinnaker.keel.clouddriver.model.ApplicationLoadBalancerModel.TargetGroupAttributes
import com.netflix.spinnaker.keel.clouddriver.model.ApplicationLoadBalancerModel.TargetGroupMatcher
import com.netflix.spinnaker.keel.clouddriver.model.Network
import com.netflix.spinnaker.keel.clouddriver.model.SecurityGroupSummary
import com.netflix.spinnaker.keel.clouddriver.model.Subnet
import com.netflix.spinnaker.keel.diff.ResourceDiff
import com.netflix.spinnaker.keel.ec2.CLOUD_PROVIDER
import com.netflix.spinnaker.keel.ec2.SPINNAKER_EC2_API_V1
import com.netflix.spinnaker.keel.ec2.resolvers.ApplicationLoadBalancerDefaultsResolver
import com.netflix.spinnaker.keel.model.OrchestrationRequest
import com.netflix.spinnaker.keel.orca.OrcaService
import com.netflix.spinnaker.keel.orca.TaskRefResponse
import com.netflix.spinnaker.keel.persistence.memory.InMemoryDeliveryConfigRepository
import com.netflix.spinnaker.keel.plugin.Resolver
import com.netflix.spinnaker.keel.serialization.configuredYamlMapper
import com.netflix.spinnaker.keel.test.resource
import de.danielbechler.diff.node.DiffNode.State.CHANGED
import de.danielbechler.diff.path.NodePath
import dev.minutest.junit.JUnit5Minutests
import dev.minutest.rootContext
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.confirmVerified
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.runBlocking
import strikt.api.expectThat
import strikt.assertions.get
import strikt.assertions.isEqualTo
import java.time.Clock
import java.util.UUID

@Suppress("UNCHECKED_CAST")
internal class ApplicationLoadBalancerSpecHandlerTests : JUnit5Minutests {
  private val cloudDriverService = mockk<CloudDriverService>()
  private val cloudDriverCache = mockk<CloudDriverCache>()
  private val orcaService = mockk<OrcaService>()
  private val clock = Clock.systemDefaultZone()
  private val environmentResolver: EnvironmentResolver = EnvironmentResolver(InMemoryDeliveryConfigRepository(clock))
  private val mapper = ObjectMapper().registerKotlinModule()
  private val yamlMapper = configuredYamlMapper()

  private val normalizers: List<Resolver<*>> = listOf(ApplicationLoadBalancerDefaultsResolver())

  private val yaml = """
    |---
    |moniker:
    |  app: testapp
    |  stack: managedogge
    |  detail: wow
    |locations:
    |  accountName: test
    |  vpcName: vpc0
    |  subnet: internal (vpc0)
    |  regions:
    |  - region: us-east-1
    |    availabilityZones:
    |    - us-east-1c
    |    - us-east-1d
    |listeners:
    | - port: 80
    |   protocol: HTTP
    |targetGroups:
    | - name: managedogge-wow-tg
    |   port: 7001
    """.trimMargin()

  private val spec = yamlMapper.readValue(yaml, ApplicationLoadBalancerSpec::class.java)
  private val resource = resource(
    apiVersion = SPINNAKER_EC2_API_V1,
    kind = "application-load-balancer",
    spec = spec
  )

  private val vpc = Network(CLOUD_PROVIDER, "vpc-23144", "vpc0", "test", "us-east-1")
  private val sub1 = Subnet("subnet-1", vpc.id, vpc.account, vpc.region, "${vpc.region}c", "internal (vpc0)")
  private val sub2 = Subnet("subnet-1", vpc.id, vpc.account, vpc.region, "${vpc.region}d", "internal (vpc0)")
  private val sg1 = SecurityGroupSummary("testapp-elb", "sg-55555")

  private val model = ApplicationLoadBalancerModel(
    moniker = null,
    loadBalancerName = "testapp-managedogge-wow",
    availabilityZones = setOf("us-east-1c", "us-east-1d"),
    vpcId = vpc.id,
    subnets = setOf(sub1.id, sub2.id),
    securityGroups = setOf(sg1.id),
    scheme = "internal",
    idleTimeout = 60,
    ipAddressType = "ipv4",
    listeners = listOf(
      ApplicationLoadBalancerListener(
        port = 80,
        protocol = "HTTP",
        certificates = null,
        rules = emptyList(),
        defaultActions = listOf(
          Action(
            order = 1,
            targetGroupName = "managedogge-wow-tg",
            type = "forward",
            redirectConfig = null)
        )
      )
    ),
    targetGroups = listOf(
      TargetGroup(
        targetGroupName = "managedogge-wow-tg",
        loadBalancerNames = listOf("testapp-managedogge-wow"),
        targetType = "instance",
        matcher = TargetGroupMatcher(httpCode = "200-299"),
        port = 7001,
        protocol = "HTTP",
        healthCheckEnabled = true,
        healthCheckTimeoutSeconds = 5,
        healthCheckPort = 7001,
        healthCheckProtocol = "HTTP",
        healthCheckPath = "/healthcheck",
        healthCheckIntervalSeconds = 10,
        healthyThresholdCount = 10,
        unhealthyThresholdCount = 2,
        vpcId = vpc.id,
        attributes = TargetGroupAttributes(
          stickinessEnabled = false,
          deregistrationDelay = 300,
          stickinessType = "lb_cookie",
          stickinessDuration = 86400,
          slowStartDurationSeconds = 0
        )
      )
    )
  )

  fun tests() = rootContext<ApplicationLoadBalancerHandler> {
    fixture {
      ApplicationLoadBalancerHandler(
        cloudDriverService,
        cloudDriverCache,
        orcaService,
        environmentResolver,
        mapper,
        normalizers
      )
    }

    before {
      with(cloudDriverCache) {
        every { networkBy(vpc.id) } returns vpc
        every { networkBy(vpc.name, vpc.account, vpc.region) } returns vpc
        every { subnetBy(sub1.id) } returns sub1
        every { subnetBy(sub2.id) } returns sub2
        every { securityGroupById(vpc.account, vpc.region, sg1.id) } returns sg1
        every { securityGroupByName(vpc.account, vpc.region, sg1.name) } returns sg1
      }

      coEvery { orcaService.orchestrate("keel@spinnaker", any()) } returns TaskRefResponse("/tasks/${UUID.randomUUID()}")
    }

    after {
      confirmVerified(orcaService)
    }

    context("the ALB does not exist") {
      before {
        coEvery { cloudDriverService.getApplicationLoadBalancer(any(), any(), any(), any(), any()) } returns emptyList()
      }

      test("the ALB is created with a generated defaultAction as none are in the spec") {
        runBlocking {
          val current = current(resource)
          val desired = desired(resource)
          upsert(resource, ResourceDiff(desired = desired, current = current))
        }

        val slot = slot<OrchestrationRequest>()
        coVerify { orcaService.orchestrate("keel@spinnaker", capture(slot)) }

        expectThat(slot.captured.job.first()) {
          get("type").isEqualTo("upsertLoadBalancer")
        }

        val listeners = slot.captured.job.first()["listeners"] as Set<ApplicationLoadBalancerSpec.Listener>

        expectThat(listeners.first()) {
          get { defaultActions.first() }.isEqualTo(model.listeners.first().defaultActions.first())
        }
      }
    }

    context("the ALB has been created") {
      before {
        coEvery { cloudDriverService.getApplicationLoadBalancer(any(), any(), any(), any(), any()) } returns listOf(model)
      }

      test("the diff is clean") {
        val diff = runBlocking {
          val current = current(resource)
          val desired = desired(resource)
          ResourceDiff(desired = desired, current = current)
        }

        expectThat(diff.diff.childCount()).isEqualTo(0)
      }
    }

    context("the ALB spec has been updated") {
      before {
        coEvery { cloudDriverService.getApplicationLoadBalancer(any(), any(), any(), any(), any()) } returns listOf(model)
      }

      test("the diff reflects the new spec and is upserted") {
        val tgroup = resource.spec.targetGroups.first().copy(port = 7505)
        val newResource = resource.copy(spec = resource.spec.copy(targetGroups = setOf(tgroup)))

        runBlocking {
          val current = current(newResource)
          val desired = desired(newResource)
          val diff = ResourceDiff(desired = desired, current = current)

          expectThat(diff.diff) {
            get { childCount() }.isEqualTo(1)
            get {
              getChild(NodePath.startBuilding().mapKey("us-east-1").propertyName("targetGroups").build()).state
            }.isEqualTo(CHANGED)
          }

          upsert(newResource, diff)
        }

        val slot = slot<OrchestrationRequest>()
        coVerify { orcaService.orchestrate("keel@spinnaker", capture(slot)) }

        expectThat(slot.captured.job.first()) {
          get("type").isEqualTo("upsertLoadBalancer")
        }
      }
    }
  }
}
