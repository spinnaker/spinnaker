package com.netflix.spinnaker.keel.ec2.resource

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import com.netflix.spinnaker.keel.api.Environment
import com.netflix.spinnaker.keel.api.Exportable
import com.netflix.spinnaker.keel.api.ec2.ClassicLoadBalancerSpec
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
import com.netflix.spinnaker.keel.ec2.SPINNAKER_EC2_API_V1
import com.netflix.spinnaker.keel.ec2.resolvers.ClassicLoadBalancerNetworkResolver
import com.netflix.spinnaker.keel.ec2.resolvers.ClassicLoadBalancerSecurityGroupsResolver
import com.netflix.spinnaker.keel.model.OrchestrationRequest
import com.netflix.spinnaker.keel.model.parseMoniker
import com.netflix.spinnaker.keel.orca.OrcaService
import com.netflix.spinnaker.keel.orca.TaskRefResponse
import com.netflix.spinnaker.keel.persistence.memory.InMemoryDeliveryConfigRepository
import com.netflix.spinnaker.keel.plugin.Resolver
import com.netflix.spinnaker.keel.plugin.TaskLauncher
import com.netflix.spinnaker.keel.serialization.configuredYamlMapper
import com.netflix.spinnaker.keel.test.resource
import de.danielbechler.diff.node.DiffNode
import de.danielbechler.diff.node.DiffNode.State
import de.danielbechler.diff.node.DiffNode.State.CHANGED
import de.danielbechler.diff.node.DiffNode.State.REMOVED
import de.danielbechler.diff.path.NodePath
import de.danielbechler.diff.selector.BeanPropertyElementSelector
import de.danielbechler.diff.selector.CollectionItemElementSelector
import de.danielbechler.diff.selector.ElementSelector
import de.danielbechler.diff.selector.MapKeyElementSelector
import dev.minutest.junit.JUnit5Minutests
import dev.minutest.rootContext
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.confirmVerified
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.springframework.context.ApplicationEventPublisher
import strikt.api.Assertion
import strikt.api.DescribeableBuilder
import strikt.api.expectThat
import strikt.assertions.get
import strikt.assertions.isEmpty
import strikt.assertions.isEqualTo
import strikt.assertions.isFalse
import strikt.assertions.isNotNull
import strikt.assertions.isTrue

@Suppress("UNCHECKED_CAST")
internal class ClassicLoadBalancerHandlerTests : JUnit5Minutests {

  private val cloudDriverService = mockk<CloudDriverService>()
  private val cloudDriverCache = mockk<CloudDriverCache>()
  private val orcaService = mockk<OrcaService>()
  private val deliveryConfigRepository: InMemoryDeliveryConfigRepository = mockk() {
    // we're just using this to get notifications
    every { environmentFor(any()) } returns Environment("test")
  }
  private val publisher: ApplicationEventPublisher = mockk(relaxUnitFun = true)
  private val taskLauncher = TaskLauncher(
    orcaService,
    deliveryConfigRepository,
    publisher
  )
  private val mapper = ObjectMapper().registerKotlinModule()
  private val yamlMapper = configuredYamlMapper()

  private val normalizers: List<Resolver<*>> = listOf(
    ClassicLoadBalancerSecurityGroupsResolver(),
    ClassicLoadBalancerNetworkResolver(cloudDriverCache))

  private val yaml = """
    |---
    |moniker:
    |  app: testapp
    |  stack: managedogge
    |  detail: wow
    |locations:
    |  account: test
    |  vpc: vpc0
    |  subnet: internal (vpc0)
    |  regions:
    |  - name: us-east-1
    |    availabilityZones:
    |    - us-east-1c
    |    - us-east-1d
    |healthCheck:
    |  target: HTTP:7001/health
    |listeners:
    | - internalProtocol: HTTP
    |   internalPort: 7001
    |   externalProtocol: HTTP
    |   externalPort: 80
    """.trimMargin()

  private val spec = yamlMapper.readValue(yaml, ClassicLoadBalancerSpec::class.java)
  private val resource = resource(
    apiVersion = SPINNAKER_EC2_API_V1,
    kind = "classic-load-balancer",
    spec = spec
  )

  private val vpc = Network(CLOUD_PROVIDER, "vpc-23144", "vpc0", "test", "us-east-1")
  private val sub1 = Subnet("subnet-1", vpc.id, vpc.account, vpc.region, "${vpc.region}c", "internal (vpc0)")
  private val sub2 = Subnet("subnet-1", vpc.id, vpc.account, vpc.region, "${vpc.region}d", "internal (vpc0)")
  private val sg1 = SecurityGroupSummary("testapp-elb", "sg-55555", "vpc-1")
  private val sg2 = SecurityGroupSummary("nondefault-elb", "sg-12345", "vpc-1")
  private val sg3 = SecurityGroupSummary("backdoor", "sg-666666", "vpc-1")

  private val listener = spec.listeners.first()

  private val model = ClassicLoadBalancerModel(
    loadBalancerName = spec.moniker.name,
    availabilityZones = spec.locations.regions.first().availabilityZones,
    vpcId = vpc.id,
    subnets = setOf(sub1.id, sub2.id),
    scheme = "internal",
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
      target = spec.healthCheck.target,
      interval = 10,
      timeout = 5,
      healthyThreshold = 5,
      unhealthyThreshold = 2
    ),
    idleTimeout = 60,
    moniker = null
  )

  fun tests() = rootContext<ClassicLoadBalancerHandler> {
    fixture {
      ClassicLoadBalancerHandler(
        cloudDriverService,
        cloudDriverCache,
        orcaService,
        taskLauncher,
        mapper,
        normalizers
      )
    }

    before {
      with(cloudDriverCache) {
        every { availabilityZonesBy(any(), any(), any(), vpc.region) } returns listOf("us-east-1c", "us-east-1d")
        every { networkBy(vpc.id) } returns vpc
        every { networkBy(vpc.name, vpc.account, vpc.region) } returns vpc
        every { subnetBy(any(), any(), any()) } returns sub1
        every { subnetBy(sub1.id) } returns sub1
        every { subnetBy(sub2.id) } returns sub2
        every { securityGroupById(vpc.account, vpc.region, sg1.id) } returns sg1
        every { securityGroupById(vpc.account, vpc.region, sg2.id) } returns sg2
        every { securityGroupById(vpc.account, vpc.region, sg3.id) } returns sg3
        every { securityGroupByName(vpc.account, vpc.region, sg1.name) } returns sg1
        every { securityGroupByName(vpc.account, vpc.region, sg2.name) } returns sg2
        every { securityGroupByName(vpc.account, vpc.region, sg3.name) } returns sg3
      }

      coEvery { orcaService.orchestrate("keel@spinnaker", any()) } returns TaskRefResponse("/tasks/${UUID.randomUUID()}")
    }

    after {
      confirmVerified(orcaService)
    }

    context("the CLB does not exist") {
      before {
        coEvery { cloudDriverService.getClassicLoadBalancer(any(), any(), any(), any(), any()) } returns emptyList()
      }

      test("the current model is empty") {
        val current = runBlocking {
          current(resource)
        }
        expectThat(current).isEmpty()
      }

      // TODO: this test should really be pulled into a test for ClassicLoadBalancerSecurityGroupsResolver
      test("the CLB is created with a default security group as none are specified in spec") {
        runBlocking {
          val current = current(resource)
          val desired = desired(resource)
          upsert(resource, ResourceDiff(desired = desired, current = current))
        }

        val slot = slot<OrchestrationRequest>()
        coVerify { orcaService.orchestrate("keel@spinnaker", capture(slot)) }

        expectThat(slot.captured.job.first()) {
          get("type").isEqualTo("upsertLoadBalancer")
          get("application").isEqualTo("testapp")
          get("subnetType").isEqualTo(sub1.purpose)
          get("securityGroups").isEqualTo(setOf("testapp-elb"))
        }
      }

      // TODO: this test should really be pulled into a test for ClassicLoadBalancerSecurityGroupsResolver
      test("no default security group is applied if any are included in the spec") {
        val modSpec = spec.run {
          copy(dependencies = dependencies.copy(securityGroupNames = setOf("nondefault-elb")))
        }
        val modResource = resource.copy(spec = modSpec)

        runBlocking {
          val current = current(modResource)
          val desired = desired(modResource)
          upsert(modResource, ResourceDiff(desired = desired, current = current))
        }

        val slot = slot<OrchestrationRequest>()
        coVerify { orcaService.orchestrate("keel@spinnaker", capture(slot)) }

        expectThat(slot.captured.job.first()) {
          get("securityGroups").isEqualTo(setOf("nondefault-elb"))
        }
      }
    }

    context("deployed CLB with a default security group") {
      before {
        coEvery { cloudDriverService.getClassicLoadBalancer(any(), any(), any(), any(), any()) } returns listOf(model)
      }

      test("computed diff removes the default security group if the spec only specifies another") {
        val newSpec = spec.run {
          copy(dependencies = dependencies.run {
            copy(securityGroupNames = setOf("nondefault-elb"))
          })
        }
        val newResource = resource.copy(spec = newSpec)

        val diff = runBlocking {
          val current = current(newResource)
          val desired = desired(newResource)
          ResourceDiff(desired = desired, current = current)
        }

        expectThat(diff.diff)
          .and {
            childCount().isEqualTo(1)
            getChild(MapKeyElementSelector("us-east-1"), BeanPropertyElementSelector("dependencies"), BeanPropertyElementSelector("securityGroupNames"))
              .isNotNull()
              .and {
                state.isEqualTo(CHANGED)
                getChild(CollectionItemElementSelector("testapp-elb")).isNotNull().state.isEqualTo(REMOVED)
              }
          }

        runBlocking {
          upsert(newResource, diff)
        }

        val slot = slot<OrchestrationRequest>()
        coVerify { orcaService.orchestrate("keel@spinnaker", capture(slot)) }
      }

      test("export generates a valid spec for the deployed CLB") {
        val exportable = Exportable(
          cloudProvider = "aws",
          account = "test",
          user = "fzlem@netflix.com",
          moniker = parseMoniker("testapp-managedogge-wow"),
          regions = setOf("us-east-1"),
          kind = supportedKind.kind
        )
        val export = runBlocking {
          export(exportable)
        }
        expectThat(export.kind)
          .isEqualTo("classic-load-balancer")

        runBlocking {
          // Export differs from the model prior to the application of resolvers
          val unresolvedDiff = ResourceDiff(resource, resource.copy(spec = export.spec))
          expectThat(unresolvedDiff.hasChanges())
            .isTrue()
          // But diffs cleanly after resolvers are applied
          val resolvedDiff = ResourceDiff(
            desired(resource),
            desired(normalize(export.copy(metadata = mapOf("serviceAccount" to "keel@spinnaker"))))
          )
          expectThat(resolvedDiff.hasChanges())
            .isFalse()
        }
      }
    }
  }
}

fun Assertion.Builder<DiffNode>.childCount(): Assertion.Builder<Int> =
  get("child count") { childCount() }

fun Assertion.Builder<DiffNode>.getChild(propertyName: String): Assertion.Builder<DiffNode?> =
  get("child node with property name $propertyName") {
    getChild(propertyName)
  }

fun Assertion.Builder<DiffNode>.getChild(vararg selectors: ElementSelector): Assertion.Builder<DiffNode?> =
  get("child node with path $path") {
    getChild(selectors.toList())
  }

val Assertion.Builder<DiffNode>.path: Assertion.Builder<NodePath>
  get() = get("path") { path }

val Assertion.Builder<DiffNode>.state: DescribeableBuilder<State>
  get() = get("path") { state }
