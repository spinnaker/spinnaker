package com.netflix.spinnaker.keel.ec2.resource

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import com.netflix.spinnaker.keel.api.Resource
import com.netflix.spinnaker.keel.api.ResourceMetadata
import com.netflix.spinnaker.keel.api.ResourceName
import com.netflix.spinnaker.keel.api.SPINNAKER_API_V1
import com.netflix.spinnaker.keel.api.ec2.ClassicLoadBalancer
import com.netflix.spinnaker.keel.api.randomUID
import com.netflix.spinnaker.keel.clouddriver.CloudDriverCache
import com.netflix.spinnaker.keel.clouddriver.CloudDriverService
import com.netflix.spinnaker.keel.clouddriver.model.ClassicLoadBalancerModel
import com.netflix.spinnaker.keel.clouddriver.model.ClassicLoadBalancerModel.ClassicLoadBalancerHealthCheck
import com.netflix.spinnaker.keel.clouddriver.model.ClassicLoadBalancerModel.ClassicLoadBalancerListener
import com.netflix.spinnaker.keel.clouddriver.model.ClassicLoadBalancerModel.ClassicLoadBalancerListenerDescription
import com.netflix.spinnaker.keel.clouddriver.model.Network
import com.netflix.spinnaker.keel.clouddriver.model.SecurityGroupSummary
import com.netflix.spinnaker.keel.clouddriver.model.Subnet
import com.netflix.spinnaker.keel.diff.ResourceDiff
import com.netflix.spinnaker.keel.ec2.CLOUD_PROVIDER
import com.netflix.spinnaker.keel.ec2.normalizers.ClassicLoadBalancerNormalizer
import com.netflix.spinnaker.keel.model.OrchestrationRequest
import com.netflix.spinnaker.keel.orca.OrcaService
import com.netflix.spinnaker.keel.orca.TaskRefResponse
import com.netflix.spinnaker.keel.plugin.ResourceNormalizer
import com.netflix.spinnaker.keel.serialization.configuredYamlMapper
import de.danielbechler.diff.node.DiffNode
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
import strikt.assertions.isNull
import java.util.UUID

@Suppress("UNCHECKED_CAST")
internal class ClassicLoadBalancerHandlerTests : JUnit5Minutests {

  private val cloudDriverService = mockk<CloudDriverService>()
  private val cloudDriverCache = mockk<CloudDriverCache>()
  private val orcaService = mockk<OrcaService>()
  private val mapper = ObjectMapper().registerKotlinModule()
  private val yamlMapper = configuredYamlMapper()

  private val normalizers: List<ResourceNormalizer<*>> = listOf(ClassicLoadBalancerNormalizer(mapper))

  private val yaml = """
    |---
    |moniker:
    |  app: testapp
    |  stack: managedogge
    |  detail: wow
    |location:
    |  accountName: test
    |  region: us-east-1
    |  availabilityZones:
    |  - us-east-1c
    |  - us-east-1d
    |vpcName: vpc0
    |subnetType: internal (vpc0)
    |healthCheck: HTTP:7001/health
    |listeners:
    | - internalProtocol: HTTP
    |   internalPort: 7001
    |   externalProtocol: HTTP
    |   externalPort: 80
    """.trimMargin()

  private val spec = yamlMapper.readValue(yaml, ClassicLoadBalancer::class.java)
  private val resource = Resource(
    SPINNAKER_API_V1.subApi("ec2"),
    "classic-load-balancer",
    ResourceMetadata(
      name = ResourceName("my-clb"),
      uid = randomUID()
    ),
    spec
  )

  private val vpc = Network(CLOUD_PROVIDER, "vpc-23144", "vpc0", "test", "us-east-1")
  private val sub1 = Subnet("subnet-1", vpc.id, vpc.account, vpc.region, "${vpc.region}c", "internal (vpc0)")
  private val sub2 = Subnet("subnet-1", vpc.id, vpc.account, vpc.region, "${vpc.region}d", "internal (vpc0)")
  private val sg1 = SecurityGroupSummary("testapp-eb", "sg-55555")
  private val sg2 = SecurityGroupSummary("nondefault-elb", "sg-12345")
  private val sg3 = SecurityGroupSummary("backdoor", "sg-666666")

  private val listener = spec.listeners.first()

  private val model = ClassicLoadBalancerModel(
    loadBalancerName = spec.moniker.name,
    availabilityZones = spec.location.availabilityZones,
    vpcId = vpc.id,
    subnets = setOf(sub1.id, sub2.id),
    securityGroups = setOf(sg1.id),
    listenerDescriptions = listOf(
      ClassicLoadBalancerListenerDescription(
        ClassicLoadBalancerListener(
          protocol = listener.externalProtocol,
          loadBalancerPort = listener.externalPort,
          instanceProtocol = listener.internalProtocol,
          instancePort = listener.internalPort,
          sslcertificateId = null
        )
      )
    ),
    healthCheck = ClassicLoadBalancerHealthCheck(
      target = spec.healthCheck,
      interval = 10,
      timeout = 5,
      healthyThreshold = 5,
      unhealthyThreshold = 2
    ),
    idleTimeout = 60,
    moniker = null,
    scheme = null
  )

  fun tests() = rootContext<ClassicLoadBalancerHandler> {
    fixture {
      ClassicLoadBalancerHandler(
        cloudDriverService,
        cloudDriverCache,
        orcaService,
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
        every { securityGroupById(vpc.account, vpc.region, sg2.id) } returns sg2
        every { securityGroupById(vpc.account, vpc.region, sg3.id) } returns sg3
        every { securityGroupByName(vpc.account, vpc.region, sg1.name) } returns sg1
        every { securityGroupByName(vpc.account, vpc.region, sg2.name) } returns sg2
        every { securityGroupByName(vpc.account, vpc.region, sg3.name) } returns sg3
      }

      coEvery { orcaService.orchestrate(any()) } returns TaskRefResponse("/tasks/${UUID.randomUUID()}")
    }

    after {
      confirmVerified(orcaService)
    }

    context("the CLB does not exist") {
      before {
        coEvery { cloudDriverService.getClassicLoadBalancer(any(), any(), any(), any()) } returns emptyList()
      }

      test("the current model is null") {
        val current = runBlocking {
          current(normalize(resource as Resource<Any>))
        }
        expectThat(current).isNull()
      }

      test("the CLB is created with a default security group as none are specified in spec") {
        runBlocking {
          val current = current(normalize(resource as Resource<Any>))
          val desired = desired(normalize(resource as Resource<Any>))
          upsert(normalize(resource as Resource<Any>), ResourceDiff(desired = desired, current = current))
        }

        val slot = slot<OrchestrationRequest>()
        coVerify { orcaService.orchestrate(capture(slot)) }

        expectThat(slot.captured.job.first()) {
          get("type").isEqualTo("upsertLoadBalancer")
          get("application").isEqualTo("testapp")
          get("subnetType").isEqualTo(sub1.purpose)
          get("securityGroups").isEqualTo(setOf("testapp-elb"))
        }
      }

      test("no default security group is applied if any are included in the spec") {
        val modSpec = spec.copy(securityGroupNames = setOf("nondefault-elb"))
        val modResource = resource.copy(spec = modSpec)

        runBlocking {
          upsert(normalize(modResource as Resource<Any>), ResourceDiff(spec, modSpec))
        }

        val slot = slot<OrchestrationRequest>()
        coVerify { orcaService.orchestrate(capture(slot)) }

        expectThat(slot.captured.job.first()) {
          get("securityGroups").isEqualTo(setOf("nondefault-elb"))
        }
      }
    }

    context("deployed CLB with a default security group") {
      before {
        coEvery { cloudDriverService.getClassicLoadBalancer(any(), any(), any(), any()) } returns listOf(model)
      }

      test("computed diff removes the default security group if the spec only specifies another") {
        val newSpec = spec.copy(securityGroupNames = setOf("nondefault-elb"))
        val newResource = resource.copy(spec = newSpec)

        val diff = runBlocking {
          val current = current(resource)
          val desired = desired(resource)
          ResourceDiff(desired, current)
        }

        expectThat(diff.diff.childCount()).isEqualTo(2)
        expectThat(diff.diff.getChild("securityGroupNames").path.toString()).isEqualTo("/securityGroupNames")
        expectThat(diff.diff.getChild("securityGroupNames").state).isEqualTo(DiffNode.State.CHANGED)

        runBlocking {
          upsert(normalize(newResource as Resource<Any>), diff)
        }

        val slot = slot<OrchestrationRequest>()
        coVerify { orcaService.orchestrate(capture(slot)) }
      }
    }
  }
}
