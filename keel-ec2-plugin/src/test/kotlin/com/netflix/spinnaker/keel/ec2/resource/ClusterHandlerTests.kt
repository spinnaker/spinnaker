package com.netflix.spinnaker.keel.ec2.resource

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import com.netflix.spinnaker.keel.api.Capacity
import com.netflix.spinnaker.keel.api.ClusterDependencies
import com.netflix.spinnaker.keel.api.Environment
import com.netflix.spinnaker.keel.api.Exportable
import com.netflix.spinnaker.keel.api.Highlander
import com.netflix.spinnaker.keel.api.RedBlack
import com.netflix.spinnaker.keel.api.SPINNAKER_API_V1
import com.netflix.spinnaker.keel.api.StaggeredRegion
import com.netflix.spinnaker.keel.api.SubmittedResource
import com.netflix.spinnaker.keel.api.SubnetAwareLocations
import com.netflix.spinnaker.keel.api.SubnetAwareRegionSpec
import com.netflix.spinnaker.keel.api.ec2.ClusterSpec
import com.netflix.spinnaker.keel.api.ec2.ClusterSpec.LaunchConfigurationSpec
import com.netflix.spinnaker.keel.api.ec2.ClusterSpec.ServerGroupSpec
import com.netflix.spinnaker.keel.api.ec2.ClusterSpec.VirtualMachineImage
import com.netflix.spinnaker.keel.api.ec2.CustomizedMetricSpecification
import com.netflix.spinnaker.keel.api.ec2.HealthCheckType
import com.netflix.spinnaker.keel.api.ec2.LaunchConfiguration
import com.netflix.spinnaker.keel.api.ec2.Metric
import com.netflix.spinnaker.keel.api.ec2.Scaling
import com.netflix.spinnaker.keel.api.ec2.ServerGroup
import com.netflix.spinnaker.keel.api.ec2.TargetTrackingPolicy
import com.netflix.spinnaker.keel.api.ec2.TerminationPolicy
import com.netflix.spinnaker.keel.api.ec2.byRegion
import com.netflix.spinnaker.keel.api.ec2.resolve
import com.netflix.spinnaker.keel.api.ec2.resolveCapacity
import com.netflix.spinnaker.keel.api.serviceAccount
import com.netflix.spinnaker.keel.clouddriver.CloudDriverCache
import com.netflix.spinnaker.keel.clouddriver.CloudDriverService
import com.netflix.spinnaker.keel.clouddriver.model.ActiveServerGroup
import com.netflix.spinnaker.keel.clouddriver.model.ActiveServerGroupImage
import com.netflix.spinnaker.keel.clouddriver.model.AutoScalingGroup
import com.netflix.spinnaker.keel.clouddriver.model.CustomizedMetricSpecificationModel
import com.netflix.spinnaker.keel.clouddriver.model.InstanceMonitoring
import com.netflix.spinnaker.keel.clouddriver.model.LaunchConfig
import com.netflix.spinnaker.keel.clouddriver.model.MetricDimensionModel
import com.netflix.spinnaker.keel.clouddriver.model.Network
import com.netflix.spinnaker.keel.clouddriver.model.ScalingPolicy
import com.netflix.spinnaker.keel.clouddriver.model.ScalingPolicyAlarm
import com.netflix.spinnaker.keel.clouddriver.model.SecurityGroupSummary
import com.netflix.spinnaker.keel.clouddriver.model.Subnet
import com.netflix.spinnaker.keel.clouddriver.model.SuspendedProcess
import com.netflix.spinnaker.keel.clouddriver.model.Tag
import com.netflix.spinnaker.keel.clouddriver.model.TargetTrackingConfiguration
import com.netflix.spinnaker.keel.diff.ResourceDiff
import com.netflix.spinnaker.keel.ec2.CLOUD_PROVIDER
import com.netflix.spinnaker.keel.ec2.RETROFIT_NOT_FOUND
import com.netflix.spinnaker.keel.ec2.SPINNAKER_EC2_API_V1
import com.netflix.spinnaker.keel.events.ArtifactVersionDeployed
import com.netflix.spinnaker.keel.model.Moniker
import com.netflix.spinnaker.keel.model.OrchestrationRequest
import com.netflix.spinnaker.keel.model.parseMoniker
import com.netflix.spinnaker.keel.orca.OrcaService
import com.netflix.spinnaker.keel.orca.TaskRefResponse
import com.netflix.spinnaker.keel.persistence.memory.InMemoryDeliveryConfigRepository
import com.netflix.spinnaker.keel.plugin.Resolver
import com.netflix.spinnaker.keel.plugin.TaskLauncher
import com.netflix.spinnaker.keel.test.resource
import dev.minutest.junit.JUnit5Minutests
import dev.minutest.rootContext
import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.confirmVerified
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import java.time.Clock
import java.time.Duration
import java.util.UUID.randomUUID
import kotlinx.coroutines.runBlocking
import org.apache.commons.lang3.RandomStringUtils.randomNumeric
import org.springframework.context.ApplicationEventPublisher
import strikt.api.Assertion
import strikt.api.expectCatching
import strikt.api.expectThat
import strikt.assertions.containsExactlyInAnyOrder
import strikt.assertions.containsKey
import strikt.assertions.get
import strikt.assertions.hasSize
import strikt.assertions.isA
import strikt.assertions.isEqualTo
import strikt.assertions.isNotEmpty
import strikt.assertions.isNotNull
import strikt.assertions.isNull
import strikt.assertions.isTrue
import strikt.assertions.map
import strikt.assertions.succeeded

internal class ClusterHandlerTests : JUnit5Minutests {

  val cloudDriverService = mockk<CloudDriverService>()
  val cloudDriverCache = mockk<CloudDriverCache>()
  val orcaService = mockk<OrcaService>()
  val objectMapper = ObjectMapper().registerKotlinModule()
  val normalizers = emptyList<Resolver<ClusterSpec>>()
  val clock = Clock.systemDefaultZone()
  val publisher: ApplicationEventPublisher = mockk(relaxUnitFun = true)
  val deliveryConfigRepository: InMemoryDeliveryConfigRepository = mockk()
  val taskLauncher = TaskLauncher(
    orcaService,
    deliveryConfigRepository
  )

  val vpcWest = Network(CLOUD_PROVIDER, "vpc-1452353", "vpc0", "test", "us-west-2")
  val vpcEast = Network(CLOUD_PROVIDER, "vpc-4342589", "vpc0", "test", "us-east-1")
  val sg1West = SecurityGroupSummary("keel", "sg-325234532", "vpc-1")
  val sg2West = SecurityGroupSummary("keel-elb", "sg-235425234", "vpc-1")
  val sg1East = SecurityGroupSummary("keel", "sg-279585936", "vpc-1")
  val sg2East = SecurityGroupSummary("keel-elb", "sg-610264122", "vpc-1")
  val subnet1West = Subnet("subnet-1", vpcWest.id, vpcWest.account, vpcWest.region, "${vpcWest.region}a", "internal (vpc0)")
  val subnet2West = Subnet("subnet-2", vpcWest.id, vpcWest.account, vpcWest.region, "${vpcWest.region}b", "internal (vpc0)")
  val subnet3West = Subnet("subnet-3", vpcWest.id, vpcWest.account, vpcWest.region, "${vpcWest.region}c", "internal (vpc0)")
  val subnet1East = Subnet("subnet-1", vpcEast.id, vpcEast.account, vpcEast.region, "${vpcEast.region}c", "internal (vpc0)")
  val subnet2East = Subnet("subnet-2", vpcEast.id, vpcEast.account, vpcEast.region, "${vpcEast.region}d", "internal (vpc0)")
  val subnet3East = Subnet("subnet-3", vpcEast.id, vpcEast.account, vpcEast.region, "${vpcEast.region}e", "internal (vpc0)")

  val targetTrackingPolicyName = "keel-test-target-tracking-policy"

  val spec = ClusterSpec(
    moniker = Moniker(app = "keel", stack = "test"),
    locations = SubnetAwareLocations(
      account = vpcWest.account,
      vpc = "vpc0",
      subnet = subnet1West.purpose!!,
      regions = listOf(vpcWest, vpcEast).map { subnet ->
        SubnetAwareRegionSpec(
          name = subnet.region,
          availabilityZones = listOf("a", "b", "c").map { "${subnet.region}$it" }.toSet()
        )
      }.toSet()
    ),
    deployWith = RedBlack(
      stagger = listOf(
        StaggeredRegion(region = vpcWest.region, hours = "10-14", pauseTime = Duration.ofMinutes(30)),
        StaggeredRegion(region = vpcEast.region, hours = "16-02")
      )
    ),
    _defaults = ServerGroupSpec(
      launchConfiguration = LaunchConfigurationSpec(
        image = VirtualMachineImage(
          id = "ami-123543254134",
          appVersion = "keel-0.287.0-h208.fe2e8a1",
          baseImageVersion = "nflx-base-5.308.0-h1044.b4b3f78"
        ),
        instanceType = "r4.8xlarge",
        ebsOptimized = false,
        iamRole = LaunchConfiguration.defaultIamRoleFor("keel"),
        keyPair = "nf-keypair-test-fake",
        instanceMonitoring = false
      ),
      capacity = Capacity(1, 6),
      scaling = Scaling(
        targetTrackingPolicies = setOf(TargetTrackingPolicy(
          name = targetTrackingPolicyName,
          targetValue = 560.0,
          disableScaleIn = true,
          customMetricSpec = CustomizedMetricSpecification(
            name = "RPS per instance",
            namespace = "SPIN/ACH",
            statistic = "Average"
          )
        ))
      ),
      dependencies = ClusterDependencies(
        loadBalancerNames = setOf("keel-test-frontend"),
        securityGroupNames = setOf(sg1West.name, sg2West.name)
      )
    )
  )

  val serverGroups = spec.resolve()
  val serverGroupEast = serverGroups.first { it.location.region == "us-east-1" }
  val serverGroupWest = serverGroups.first { it.location.region == "us-west-2" }

  val resource = resource(
    apiVersion = SPINNAKER_API_V1,
    kind = "cluster",
    spec = spec
  )

  val activeServerGroupResponseEast = serverGroupEast.toCloudDriverResponse(vpcEast, listOf(subnet1East, subnet2East, subnet3East), listOf(sg1East, sg2East))
  val activeServerGroupResponseWest = serverGroupWest.toCloudDriverResponse(vpcWest, listOf(subnet1West, subnet2West, subnet3West), listOf(sg1West, sg2West))

  val exportable = Exportable(
    cloudProvider = "aws",
    account = spec.locations.account,
    user = "fzlem@netflix.com",
    moniker = spec.moniker,
    regions = spec.locations.regions.map { it.name }.toSet(),
    kind = "cluster"
  )

  private fun ServerGroup.toCloudDriverResponse(
    vpc: Network,
    subnets: List<Subnet>,
    securityGroups: List<SecurityGroupSummary>
  ): ActiveServerGroup =
    randomNumeric(3).padStart(3, '0').let { sequence ->
      ActiveServerGroup(
        "$name-v$sequence",
        location.region,
        location.availabilityZones,
        ActiveServerGroupImage(
          launchConfiguration.imageId,
          launchConfiguration.appVersion,
          launchConfiguration.baseImageVersion
        ),
        LaunchConfig(
          launchConfiguration.ramdiskId,
          launchConfiguration.ebsOptimized,
          launchConfiguration.imageId,
          launchConfiguration.instanceType,
          launchConfiguration.keyPair,
          launchConfiguration.iamRole,
          InstanceMonitoring(launchConfiguration.instanceMonitoring)
        ),
        AutoScalingGroup(
          "$name-v$sequence",
          health.cooldown.seconds,
          health.healthCheckType.let(HealthCheckType::toString),
          health.warmup.seconds,
          scaling.suspendedProcesses.map { SuspendedProcess(it.name) }.toSet(),
          health.enabledMetrics.map(Metric::toString).toSet(),
          tags.map { Tag(it.key, it.value) }.toSet(),
          health.terminationPolicies.map(TerminationPolicy::toString).toSet(),
          subnets.map(Subnet::id).joinToString(",")
        ),
        listOf(ScalingPolicy(
          autoScalingGroupName = "$name-v$sequence",
          policyName = "$name-target-tracking-policy",
          policyType = "TargetTrackingScaling",
          estimatedInstanceWarmup = 300,
          adjustmentType = null,
          minAdjustmentStep = null,
          minAdjustmentMagnitude = null,
          stepAdjustments = null,
          metricAggregationType = null,
          targetTrackingConfiguration = TargetTrackingConfiguration(
            560.0,
            true,
            CustomizedMetricSpecificationModel(
              "RPS per instance",
              "SPIN/ACH",
              "Average",
              null,
              listOf(MetricDimensionModel("AutoScalingGroupName", "$name-v$sequence"))
            ),
            null
          ),
          alarms = listOf(ScalingPolicyAlarm(
            true,
            "GreaterThanThreshold",
            listOf(MetricDimensionModel("AutoScalingGroupName", "$name-v$sequence")),
            3,
            60,
            560,
            "RPS per instance",
            "SPIN/ACH",
            "Average"
          ))
        )),
        vpc.id,
        dependencies.targetGroups,
        dependencies.loadBalancerNames,
        capacity.let { Capacity(it.min, it.max, it.desired) },
        CLOUD_PROVIDER,
        securityGroups.map(SecurityGroupSummary::id).toSet(),
        location.account,
        parseMoniker("$name-v$sequence")
      )
    }

  fun tests() = rootContext<ClusterHandler> {
    fixture {
      ClusterHandler(
        cloudDriverService,
        cloudDriverCache,
        orcaService,
        taskLauncher,
        clock,
        publisher,
        objectMapper,
        normalizers
      )
    }

    before {
      with(cloudDriverCache) {
        every { defaultKeyPairForAccount("test") } returns "nf-keypair-test-{{region}}"

        every { networkBy(vpcWest.id) } returns vpcWest
        every { subnetBy(subnet1West.id) } returns subnet1West
        every { subnetBy(subnet2West.id) } returns subnet2West
        every { subnetBy(subnet3West.id) } returns subnet3West
        every { subnetBy(vpcWest.account, vpcWest.region, subnet1West.purpose!!) } returns subnet1West
        every { securityGroupById(vpcWest.account, vpcWest.region, sg1West.id) } returns sg1West
        every { securityGroupById(vpcWest.account, vpcWest.region, sg2West.id) } returns sg2West
        every { securityGroupByName(vpcWest.account, vpcWest.region, sg1West.name) } returns sg1West
        every { securityGroupByName(vpcWest.account, vpcWest.region, sg2West.name) } returns sg2West
        every { availabilityZonesBy(vpcWest.account, vpcWest.id, subnet1West.purpose!!, vpcWest.region) } returns
          setOf(subnet1West.availabilityZone)

        every { networkBy(vpcEast.id) } returns vpcEast
        every { subnetBy(subnet1East.id) } returns subnet1East
        every { subnetBy(subnet2East.id) } returns subnet2East
        every { subnetBy(subnet3East.id) } returns subnet3East
        every { subnetBy(vpcEast.account, vpcEast.region, subnet1East.purpose!!) } returns subnet1East
        every { securityGroupById(vpcEast.account, vpcEast.region, sg1East.id) } returns sg1East
        every { securityGroupById(vpcEast.account, vpcEast.region, sg2East.id) } returns sg2East
        every { securityGroupByName(vpcEast.account, vpcEast.region, sg1East.name) } returns sg1East
        every { securityGroupByName(vpcEast.account, vpcEast.region, sg2East.name) } returns sg2East
        every { availabilityZonesBy(vpcEast.account, vpcEast.id, subnet1East.purpose!!, vpcEast.region) } returns
          setOf(subnet1East.availabilityZone)
      }

      coEvery { orcaService.orchestrate(resource.serviceAccount, any()) } returns TaskRefResponse("/tasks/${randomUUID()}")
      every { deliveryConfigRepository.environmentFor(any()) } returns Environment("test")
    }

    after {
      confirmVerified(orcaService)
      clearAllMocks()
    }

    context("the cluster does not exist or has no active server groups") {
      before {
        coEvery { cloudDriverService.activeServerGroup(any(), "us-east-1") } returns activeServerGroupResponseEast
        coEvery { cloudDriverService.activeServerGroup(any(), "us-west-2") } throws RETROFIT_NOT_FOUND
      }

      test("the current model is null") {
        val current = runBlocking {
          current(resource)
        }
        expectThat(current)
          .hasSize(1)
          .not()
          .containsKey("us-west-2")
      }

      test("annealing a staggered cluster with simple capacity doesn't attempt to upsertScalingPolicy") {
        runBlocking {
          upsert(
            resource,
            ResourceDiff(
              serverGroups.map {
                it.copy(scaling = Scaling(), capacity = Capacity(2, 2, 2))
              }.byRegion(),
              emptyMap()))
        }

        val slot = slot<OrchestrationRequest>()
        coVerify { orcaService.orchestrate(resource.serviceAccount, capture(slot)) }

        // slot will only contain the last orchestration request made, which should
        // always be for the second staggered region (east).
        expectThat(slot.captured.job.size).isEqualTo(2)
        expectThat(slot.captured.job.first()) {
          // east is waiting for west
          get("type").isEqualTo("dependsOnExecution")
        }
        expectThat(slot.captured.job[1]) {
          get("type").isEqualTo("createServerGroup")
          get("refId").isEqualTo("2")
          get("requisiteStageRefIds")
            .isA<List<String>>()
            .isEqualTo(listOf("1"))
          get("availabilityZones")
            .isA<Map<String, Set<String>>>()
            .hasSize(1)
            .containsKey("us-east-1")
          get("restrictExecutionDuringTimeWindow").isEqualTo(true)
        }
      }

      test("annealing a diff creates staggered server groups with scaling policies upserted in the same orchestration") {
        runBlocking {
          upsert(resource, ResourceDiff(serverGroups.byRegion(), emptyMap()))
        }

        val slot = slot<OrchestrationRequest>()
        coVerify { orcaService.orchestrate(resource.serviceAccount, capture(slot)) }

        expectThat(slot.captured.job.size).isEqualTo(3)
        expectThat(slot.captured.job.first()) {
          // east is waiting for west
          get("type").isEqualTo("dependsOnExecution")
        }
        expectThat(slot.captured.job[1]) {
          get("type").isEqualTo("createServerGroup")
          get("refId").isEqualTo("2")
          get("requisiteStageRefIds")
            .isA<List<String>>()
            .isEqualTo(listOf("1"))
          get("availabilityZones")
            .isA<Map<String, Set<String>>>()
            .hasSize(1)
            .containsKey("us-east-1")
          get("restrictExecutionDuringTimeWindow").isEqualTo(true)
        }
        expectThat(slot.captured.job[2]) {
          get("type").isEqualTo("upsertScalingPolicy")
          get("refId").isEqualTo("3")
          get("requisiteStageRefIds")
            .isA<List<String>>()
            .isEqualTo(listOf("2"))
          get("restrictExecutionDuringTimeWindow").isNull()
        }
      }
    }

    context("the cluster has active server groups") {
      before {
        coEvery { cloudDriverService.activeServerGroup(any(), "us-east-1") } returns activeServerGroupResponseEast
        coEvery { cloudDriverService.activeServerGroup(any(), "us-west-2") } returns activeServerGroupResponseWest
      }

      // TODO: test for multiple server group response
      derivedContext<Map<String, ServerGroup>>("fetching the current server group state") {
        deriveFixture {
          runBlocking {
            current(resource)
          }
        }

        test("the current model is converted to a set of server group") {
          expectThat(this).isNotEmpty()
        }

        test("the server group name is derived correctly") {
          expectThat(values)
            .map { it.name }
            .containsExactlyInAnyOrder(
              activeServerGroupResponseEast.name,
              activeServerGroupResponseWest.name
            )
        }

        test("an event is fired if all server groups have the same artifact version") {
          verify { publisher.publishEvent(ofType<ArtifactVersionDeployed>()) }
        }
      }

      derivedContext<SubmittedResource<ClusterSpec>>("exported cluster spec") {
        deriveFixture {
          runBlocking {
            export(exportable)
          }
        }

        test("has the expected basic properties") {
          expectThat(kind)
            .isEqualTo("cluster")
          expectThat(apiVersion)
            .isEqualTo(SPINNAKER_EC2_API_V1)
          expectThat(spec.locations.regions)
            .hasSize(2)
          expectThat(spec.defaults.scaling!!.targetTrackingPolicies)
            .hasSize(1)
          expectThat(spec.overrides)
            .hasSize(0)
        }

        test("omits complex fields altogether when all their properties have default values") {
          expectThat(spec.defaults.health)
            .isNull()
        }
      }

      context("other handling of default properties in cluster export") {
        before {
          coEvery { cloudDriverService.activeServerGroup(any(), "us-east-1") } returns activeServerGroupResponseEast
          coEvery { cloudDriverService.activeServerGroup(any(), "us-west-2") } returns activeServerGroupResponseWest
            .withNonDefaultHealthProps()
            .withNonDefaultLaunchConfigProps()
        }

        test("export omits properties with default values from complex fields") {
          val exported = runBlocking {
            export(exportable)
          }
          expectThat(exported.spec.locations.vpc)
            .isNull()
          expectThat(exported.spec.locations.subnet)
            .isNull()
          expectThat(exported.spec.defaults.health)
            .isNotNull()
          expectThat(exported.spec.defaults.health!!.cooldown)
            .isNull()
          expectThat(exported.spec.defaults.health!!.warmup)
            .isNull()
          expectThat(exported.spec.defaults.health!!.healthCheckType)
            .isNull()
          expectThat(exported.spec.defaults.health!!.enabledMetrics)
            .isNull()
          expectThat(exported.spec.defaults.health!!.cooldown)
            .isNull()
          expectThat(exported.spec.defaults.health!!.terminationPolicies)
            .isEqualTo(setOf(TerminationPolicy.NewestInstance))

          expectThat(exported.spec.defaults.launchConfiguration!!.ebsOptimized)
            .isNull()
          expectThat(exported.spec.defaults.launchConfiguration!!.instanceMonitoring)
            .isNull()
          expectThat(exported.spec.defaults.launchConfiguration!!.ramdiskId)
            .isNull()
          expectThat(exported.spec.defaults.launchConfiguration!!.iamRole)
            .isNotNull()
          expectThat(exported.spec.defaults.launchConfiguration!!.keyPair)
            .isNotNull()
        }
      }
    }

    context("the cluster has active server groups with different app versions") {
      before {
        coEvery { cloudDriverService.activeServerGroup(any(), "us-east-1") } returns activeServerGroupResponseEast
        coEvery { cloudDriverService.activeServerGroup(any(), "us-west-2") } returns activeServerGroupResponseWest.withOlderAppVersion()

        runBlocking {
          current(resource)
        }
      }

      test("no event is fired indicating an app version is deployed") {
        verify(exactly = 0) { publisher.publishEvent(ofType<ArtifactVersionDeployed>()) }
      }
    }

    context("the cluster has active server groups with missing app version tag in one region") {
      before {
        coEvery { cloudDriverService.activeServerGroup(any(), "us-east-1") } returns activeServerGroupResponseEast
        coEvery { cloudDriverService.activeServerGroup(any(), "us-west-2") } answers { activeServerGroupResponseWest.withMissingAppVersion() }
      }

      test("app version is null in the region with missing tag") {
        val current = runBlocking {
          current(resource)
        }
        expectThat(current).containsKey("us-west-2")
        expectThat(current["us-west-2"]!!.launchConfiguration.appVersion).isNull()
      }

      test("no exception is thrown") {
        expectCatching {
          runBlocking {
            current(resource)
          }
        }.succeeded()
      }

      test("no event is fired indicating an app version is deployed") {
        runBlocking {
          current(resource)
        }
        verify(exactly = 0) { publisher.publishEvent(ofType<ArtifactVersionDeployed>()) }
      }

      test("applying the diff creates a server group in the region with missing tag") {
        val modified = setOf(
          serverGroupEast.copy(name = activeServerGroupResponseEast.name),
          serverGroupWest.copy(name = activeServerGroupResponseWest.name).withMissingAppVersion()
        )
        val diff = ResourceDiff(
          serverGroups.byRegion(),
          modified.byRegion()
        )
        runBlocking {
          upsert(resource, diff)
        }

        val slot = slot<OrchestrationRequest>()
        coVerify { orcaService.orchestrate(resource.serviceAccount, capture(slot)) }

        expectThat(slot.captured.job.first()) {
          get("type").isEqualTo("createServerGroup")
          get { get("availabilityZones") as Map<String, String> }.containsKey("us-west-2")
        }
      }
    }

    context("a diff has been detected") {
      context("the diff is only in capacity") {
        val modified = setOf(
          serverGroupEast.copy(name = activeServerGroupResponseEast.name),
          serverGroupWest.copy(name = activeServerGroupResponseWest.name).withDoubleCapacity()
        )
        val diff = ResourceDiff(
          serverGroups.byRegion(),
          modified.byRegion()
        )

        test("annealing resizes the current server group with no stagger") {
          runBlocking {
            upsert(resource, diff)
          }

          val slot = slot<OrchestrationRequest>()
          coVerify { orcaService.orchestrate(resource.serviceAccount, capture(slot)) }

          expectThat(slot.captured.job.first()) {
            get("type").isEqualTo("resizeServerGroup")
            get("capacity").isEqualTo(
              spec.resolveCapacity("us-west-2").let {
                mapOf(
                  "min" to it.min,
                  "max" to it.max,
                  "desired" to it.desired
                )
              }
            )
            get("serverGroupName").isEqualTo(activeServerGroupResponseWest.asg.autoScalingGroupName)
          }
        }
      }

      context("the diff is only in scaling policies missing from current") {
        val modified = setOf(
          serverGroupEast.copy(name = activeServerGroupResponseEast.name),
          serverGroupWest.copy(name = activeServerGroupResponseWest.name).withNoScalingPolicies()
        )
        val diff = ResourceDiff(
          serverGroups.byRegion(),
          modified.byRegion()
        )

        test("annealing only upserts scaling policies on the current server group") {
          val metricSpec = serverGroupWest.scaling.targetTrackingPolicies.first().customMetricSpec!!
          runBlocking {
            upsert(resource, diff)
          }

          val slot = slot<OrchestrationRequest>()
          coVerify { orcaService.orchestrate(resource.serviceAccount, capture(slot)) }

          expectThat(slot.captured.job.size).isEqualTo(1)
          expectThat(slot.captured.job.first()) {
            get("type").isEqualTo("upsertScalingPolicy")
            get("targetTrackingConfiguration")
              .isA<Map<String, Any?>>()
              .get {
                expectThat(get("targetValue"))
                  .isA<Double>()
                  .isEqualTo(560.0)
                expectThat(get("disableScaleIn"))
                  .isA<Boolean>()
                  .isTrue()
                expectThat(get("customizedMetricSpecification"))
                  .isA<CustomizedMetricSpecificationModel>()
                  .isEqualTo(
                    CustomizedMetricSpecificationModel(
                      metricName = metricSpec.name,
                      namespace = metricSpec.namespace,
                      statistic = metricSpec.statistic
                    )
                  )
              }
          }
        }
      }

      context("the diff is only that deployed scaling policies are no longer desired") {
        val modified = setOf(
          serverGroupEast.copy(name = activeServerGroupResponseEast.name),
          serverGroupWest.copy(name = activeServerGroupResponseWest.name).withNoScalingPolicies()
        )
        val diff = ResourceDiff(
          modified.byRegion(),
          serverGroups.byRegion()
        )

        test("annealing only deletes policies from the current server group") {
          runBlocking {
            upsert(resource, diff)
          }

          val slot = slot<OrchestrationRequest>()
          coVerify { orcaService.orchestrate(resource.serviceAccount, capture(slot)) }

          expectThat(slot.captured.job.size).isEqualTo(1)
          expectThat(slot.captured.job.first()) {
            get("type").isEqualTo("deleteScalingPolicy")
            get("policyName").isEqualTo(targetTrackingPolicyName)
          }
        }
      }

      context("only an existing scaling policy has been modified") {
        val modified = setOf(
          serverGroupEast.copy(name = activeServerGroupResponseEast.name),
          serverGroupWest.copy(
            name = activeServerGroupResponseWest.name,
            scaling = serverGroupWest.scaling.copy(
              targetTrackingPolicies = setOf(
                serverGroupWest.scaling.targetTrackingPolicies
                  .first()
                  .copy(targetValue = 42.0)
              )
            )
          )
        )
        val diff = ResourceDiff(
          modified.byRegion(),
          serverGroups.byRegion()
        )

        test("the modified policy is applied in two phases via one task") {
          runBlocking {
            upsert(resource, diff)
          }

          val slot = slot<OrchestrationRequest>()
          coVerify { orcaService.orchestrate(resource.serviceAccount, capture(slot)) }

          expectThat(slot.captured.job.size).isEqualTo(2)
          expectThat(slot.captured.job.first()) {
            get("refId").isEqualTo("1")
            get("requisiteStageRefIds")
              .isA<List<String>>()
              .isEqualTo(emptyList())
            get("type").isEqualTo("deleteScalingPolicy")
            get("policyName").isEqualTo(targetTrackingPolicyName)
          }
          expectThat(slot.captured.job[1]) {
            get("refId").isEqualTo("2")
            get("requisiteStageRefIds")
              .isA<List<String>>()
              .isEqualTo(listOf("1"))
            get("type").isEqualTo("upsertScalingPolicy")
            get("targetTrackingConfiguration")
              .isA<Map<String, Any?>>()["targetValue"]
              .isEqualTo(42.0)
          }
        }
      }

      context("the diff is only in capacity and scaling policies") {
        val modified = setOf(
          serverGroupEast.copy(name = activeServerGroupResponseEast.name),
          serverGroupWest.copy(name = activeServerGroupResponseWest.name)
            .withDoubleCapacity()
            .withNoScalingPolicies()
        )
        val diff = ResourceDiff(
          serverGroups.byRegion(),
          modified.byRegion()
        )

        test("annealing resizes and modifies scaling policies in-place on the current server group") {
          runBlocking {
            upsert(resource, diff)
          }

          val slot = slot<OrchestrationRequest>()
          coVerify { orcaService.orchestrate(resource.serviceAccount, capture(slot)) }

          expectThat(slot.captured.job.size).isEqualTo(2)
          expectThat(slot.captured.job.first()) {
            get("refId").isEqualTo("1")
            get("type").isEqualTo("resizeServerGroup")
          }
          expectThat(slot.captured.job[1]) {
            get("refId").isEqualTo("2")
            get("type").isEqualTo("upsertScalingPolicy")
            get("targetTrackingConfiguration")
              .isA<Map<String, Any?>>()["targetValue"]
              .isEqualTo(560.0)
          }
        }
      }

      context("the diff is something other than just capacity or scaling policies") {
        val modified = setOf(
          serverGroupEast.copy(name = activeServerGroupResponseEast.name),
          serverGroupWest.copy(name = activeServerGroupResponseWest.name)
            .withDoubleCapacity()
            .withDifferentInstanceType()
        )
        val diff = ResourceDiff(
          serverGroups.byRegion(),
          modified.byRegion()
        )

        test("annealing clones the current server group") {
          runBlocking {
            upsert(resource, diff)
          }

          val slot = slot<OrchestrationRequest>()
          coVerify { orcaService.orchestrate(resource.serviceAccount, capture(slot)) }

          expectThat(slot.captured.job.first()) {
            get("type").isEqualTo("createServerGroup")
            get("source").isEqualTo(
              mapOf(
                "account" to activeServerGroupResponseWest.accountName,
                "region" to activeServerGroupResponseWest.region,
                "asgName" to activeServerGroupResponseWest.asg.autoScalingGroupName
              )
            )
          }
        }

        test("the default deploy strategy is used") {
          val deployWith = RedBlack()
          runBlocking {
            upsert(resource, diff)
          }

          val slot = slot<OrchestrationRequest>()
          coVerify { orcaService.orchestrate(resource.serviceAccount, capture(slot)) }

          expectThat(slot.captured.job.first()) {
            get("strategy").isEqualTo("redblack")
            get("delayBeforeDisableSec").isEqualTo(deployWith.delayBeforeDisable.seconds)
            get("delayBeforeScaleDownSec").isEqualTo(deployWith.delayBeforeScaleDown.seconds)
            get("rollback").isA<Map<String, Any?>>().get("onFailure").isEqualTo(deployWith.rollbackOnFailure)
            get("scaleDown").isEqualTo(deployWith.resizePreviousToZero)
            get("maxRemainingAsgs").isEqualTo(deployWith.maxServerGroups)
          }
        }

        test("the deploy strategy is configured") {
          val deployWith = RedBlack(
            resizePreviousToZero = true,
            delayBeforeDisable = Duration.ofMinutes(1),
            delayBeforeScaleDown = Duration.ofMinutes(5),
            maxServerGroups = 3
          )
          runBlocking {
            upsert(resource.copy(spec = resource.spec.copy(deployWith = deployWith)), diff)
          }

          val slot = slot<OrchestrationRequest>()
          coVerify { orcaService.orchestrate(resource.serviceAccount, capture(slot)) }

          expectThat(slot.captured.job.first()) {
            get("strategy").isEqualTo("redblack")
            get("delayBeforeDisableSec").isEqualTo(deployWith.delayBeforeDisable.seconds)
            get("delayBeforeScaleDownSec").isEqualTo(deployWith.delayBeforeScaleDown.seconds)
            get("rollback").isA<Map<String, Any?>>().get("onFailure").isEqualTo(deployWith.rollbackOnFailure)
            get("scaleDown").isEqualTo(deployWith.resizePreviousToZero)
            get("maxRemainingAsgs").isEqualTo(deployWith.maxServerGroups)
          }
        }

        test("a different deploy strategy is used") {
          runBlocking {
            upsert(resource.copy(spec = resource.spec.copy(deployWith = Highlander)), diff)
          }

          val slot = slot<OrchestrationRequest>()
          coVerify { orcaService.orchestrate(resource.serviceAccount, capture(slot)) }

          expectThat(slot.captured.job.first()) {
            get("strategy").isEqualTo("highlander")
            not().containsKey("delayBeforeDisableSec")
            not().containsKey("delayBeforeScaleDownSec")
            not().containsKey("rollback")
            not().containsKey("scaleDown")
            not().containsKey("maxRemainingAsgs")
          }
        }
      }

      context("multiple server groups have a diff") {
        val modified = setOf(
          serverGroupEast.copy(name = activeServerGroupResponseEast.name).withDifferentInstanceType(),
          serverGroupWest.copy(name = activeServerGroupResponseWest.name).withDoubleCapacity()
        )
        val diff = ResourceDiff(
          serverGroups.byRegion(),
          modified.byRegion()
        )

        before {
          runBlocking {
            upsert(resource, diff)
          }
        }

        test("annealing launches one task per server group") {
          val tasks = mutableListOf<OrchestrationRequest>()
          coVerify { orcaService.orchestrate(any(), capture(tasks)) }

          expectThat(tasks)
            .hasSize(2)
            .map { it.job.first()["type"] }
            .containsExactlyInAnyOrder("createServerGroup", "resizeServerGroup")
        }

        test("each task has a distinct correlation id") {
          val tasks = mutableListOf<OrchestrationRequest>()
          coVerify { orcaService.orchestrate(any(), capture(tasks)) }

          expectThat(tasks)
            .hasSize(2)
            .map { it.trigger.correlationId }
            .containsDistinctElements()
        }
      }
    }
  }

  private suspend fun CloudDriverService.activeServerGroup(user: String, region: String) = activeServerGroup(
    user = user,
    app = spec.moniker.app,
    account = spec.locations.account,
    cluster = spec.moniker.name,
    region = region,
    cloudProvider = CLOUD_PROVIDER
  )
}

private fun <E, T : Iterable<E>> Assertion.Builder<T>.containsDistinctElements() =
  assert("contains distinct elements") { subject ->
    val duplicates = subject
      .associateWith { elem -> subject.count { it == elem } }
      .filterValues { it > 1 }
      .keys
    when (duplicates.size) {
      0 -> pass()
      1 -> fail(duplicates.first(), "The element %s occurs more than once")
      else -> fail(duplicates, "The elements %s occur more than once")
    }
  }

private fun ServerGroup.withDoubleCapacity(): ServerGroup =
  copy(
    capacity = Capacity(
      min = capacity.min * 2,
      max = capacity.max * 2,
      desired = when (capacity.desired) {
        null -> null
        else -> capacity.desired!! * 2
      }
    )
  )

private fun ServerGroup.withNoScalingPolicies(): ServerGroup =
  copy(scaling = Scaling(), capacity = capacity.copy(desired = capacity.max))

private fun ServerGroup.withDifferentInstanceType(): ServerGroup =
  copy(
    launchConfiguration = launchConfiguration.copy(
      instanceType = "r4.16xlarge"
    )
  )

private fun ServerGroup.withMissingAppVersion(): ServerGroup =
  copy(
    launchConfiguration = launchConfiguration.copy(
      appVersion = null
    )
  )

private fun ActiveServerGroup.withOlderAppVersion(): ActiveServerGroup =
  copy(
    image = image.copy(
      imageId = "ami-573e1b2650a5",
      appVersion = "keel-0.251.0-h167.9ea0465"
    ),
    launchConfig = launchConfig.copy(
      imageId = "ami-573e1b2650a5"
    )
  )

private fun ActiveServerGroup.withNonDefaultHealthProps(): ActiveServerGroup =
  copy(
    asg = asg.copy(terminationPolicies = setOf(TerminationPolicy.NewestInstance.name)
    )
  )

private fun ActiveServerGroup.withNonDefaultLaunchConfigProps(): ActiveServerGroup =
  copy(
    launchConfig = launchConfig.copy(iamInstanceProfile = "NotTheDefaultInstanceProfile", keyName = "not-the-default-key")
  )

private fun ActiveServerGroup.withMissingAppVersion(): ActiveServerGroup =
  copy(
    image = ActiveServerGroupImage(
      imageId = "ami-573e1b2650a5",
      tags = emptyList()
    )
  )
