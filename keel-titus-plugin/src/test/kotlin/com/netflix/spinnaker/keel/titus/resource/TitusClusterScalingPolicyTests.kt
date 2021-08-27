package com.netflix.spinnaker.keel.titus.resource

import com.netflix.spinnaker.keel.api.Moniker
import com.netflix.spinnaker.keel.api.SimpleLocations
import com.netflix.spinnaker.keel.api.SimpleRegionSpec
import com.netflix.spinnaker.keel.api.actuation.Job
import com.netflix.spinnaker.keel.api.actuation.Task
import com.netflix.spinnaker.keel.api.actuation.TaskLauncher
import com.netflix.spinnaker.keel.api.actuation.type
import com.netflix.spinnaker.keel.api.ec2.ClusterSpec.CapacitySpec
import com.netflix.spinnaker.keel.api.ec2.CustomizedMetricSpecification
import com.netflix.spinnaker.keel.api.ec2.MetricDimension
import com.netflix.spinnaker.keel.api.ec2.StepAdjustment
import com.netflix.spinnaker.keel.api.ec2.TargetTrackingPolicy
import com.netflix.spinnaker.keel.api.titus.TITUS_CLOUD_PROVIDER
import com.netflix.spinnaker.keel.api.titus.TITUS_CLUSTER_V1
import com.netflix.spinnaker.keel.api.titus.TitusClusterSpec
import com.netflix.spinnaker.keel.api.titus.TitusScalingSpec
import com.netflix.spinnaker.keel.clouddriver.CloudDriverService
import com.netflix.spinnaker.keel.clouddriver.model.Capacity
import com.netflix.spinnaker.keel.clouddriver.model.Constraints
import com.netflix.spinnaker.keel.clouddriver.model.CustomizedMetricSpecificationModel
import com.netflix.spinnaker.keel.clouddriver.model.DockerImage
import com.netflix.spinnaker.keel.clouddriver.model.InstanceCounts
import com.netflix.spinnaker.keel.clouddriver.model.MetricDimensionModel
import com.netflix.spinnaker.keel.clouddriver.model.MigrationPolicy
import com.netflix.spinnaker.keel.clouddriver.model.Placement
import com.netflix.spinnaker.keel.clouddriver.model.Resources
import com.netflix.spinnaker.keel.clouddriver.model.ServerGroupCollection
import com.netflix.spinnaker.keel.clouddriver.model.ServiceJobProcesses
import com.netflix.spinnaker.keel.clouddriver.model.StepAdjustmentModel
import com.netflix.spinnaker.keel.clouddriver.model.StepPolicyDescriptor
import com.netflix.spinnaker.keel.clouddriver.model.StepScalingAlarm
import com.netflix.spinnaker.keel.clouddriver.model.StepScalingPolicy
import com.netflix.spinnaker.keel.clouddriver.model.TargetPolicyDescriptor
import com.netflix.spinnaker.keel.clouddriver.model.TitusActiveServerGroup
import com.netflix.spinnaker.keel.clouddriver.model.TitusActiveServerGroupImage
import com.netflix.spinnaker.keel.clouddriver.model.TitusScaling
import com.netflix.spinnaker.keel.clouddriver.model.TitusScaling.Policy.StepPolicy
import com.netflix.spinnaker.keel.clouddriver.model.TitusScaling.Policy.TargetPolicy
import com.netflix.spinnaker.keel.core.api.randomUID
import com.netflix.spinnaker.keel.diff.DefaultResourceDiff
import com.netflix.spinnaker.keel.docker.DigestProvider
import com.netflix.spinnaker.keel.test.resource
import com.netflix.spinnaker.keel.titus.TitusClusterHandler
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import strikt.api.expectThat
import strikt.assertions.contains
import strikt.assertions.doesNotContain
import strikt.assertions.first
import strikt.assertions.get
import strikt.assertions.isA
import strikt.assertions.isEqualTo
import strikt.assertions.isNotEmpty
import strikt.assertions.isNotNull
import strikt.assertions.map
import strikt.mockk.withCaptured
import java.time.Clock
import java.time.Duration
import java.util.UUID.randomUUID
import io.mockk.coEvery as every

class TitusClusterScalingPolicyTests {

  val application = "fnord"
  val account = "titustestvpc"
  val cluster = "$application-test"
  val region = "ap-south-1"
  val asg = "$cluster-v193"

  val actualServerGroup = TitusActiveServerGroup(
    id = randomUUID().toString(),
    name = asg,
    awsAccount = account,
    placement = Placement(account, region),
    region = region,
    image = TitusActiveServerGroupImage(
      dockerImageName = "$application/$application",
      dockerImageVersion = "v1.1.409-h1404.6d21b5a",
      dockerImageDigest = "sha256:fe6ab5b1ffadbc147e5f0f89ea7fbc68f3de12b112f22a6a0e3c71fe807e5fc3"
    ),
    iamProfile = "arn:aws:iam::149510111645:role/${application}InstanceProfile",
    entryPoint = "",
    targetGroups = emptySet(),
    loadBalancers = emptySet(),
    securityGroups = emptySet(),
    capacity = Capacity(min = 1, max = 10, desired = 5),
    cloudProvider = TITUS_CLOUD_PROVIDER,
    moniker = Moniker(app = application, stack = "test"),
    env = mapOf(
      "EC2_REGION" to region,
      "NETFLIX_REGION" to region,
      "NETFLIX_HOME_REGION" to region
    ),
    migrationPolicy = MigrationPolicy(),
    serviceJobProcesses = ServiceJobProcesses(),
    constraints = Constraints(),
    tags = emptyMap(),
    resources = Resources(),
    capacityGroup = application,
    instanceCounts = InstanceCounts(
      total = 5,
      up = 5,
      down = 0,
      unknown = 0,
      outOfService = 0,
      starting = 0
    ),
    createdTime = 0L,
    scalingPolicies = listOf(
      TitusScaling(
        id = randomUUID().toString(),
        policy = TargetPolicy(
          targetPolicyDescriptor = TargetPolicyDescriptor(
            targetValue = 35.0,
            scaleOutCooldownSec = 300,
            scaleInCooldownSec = 300,
            disableScaleIn = true,
            customizedMetricSpecification = CustomizedMetricSpecificationModel(
              namespace = "NFLX/EPIC",
              metricName = "AverageCPUUtilization",
              statistic = "Average",
              dimensions = listOf(
                MetricDimensionModel(
                  name = "AutoScalingGroupName",
                  value = asg
                )
              )
            )
          )
        )
      ),
      TitusScaling(
        id = randomUUID().toString(),
        policy = StepPolicy(
          stepPolicyDescriptor = StepPolicyDescriptor(
            alarmConfig = StepScalingAlarm(
              comparisonOperator = "LessThanOrEqualToThreshold",
              evaluationPeriods = 60,
              periodSec = 60,
              threshold = 20.0,
              metricNamespace = "NFLX/EPIC",
              metricName = "AverageCPUUtilization",
              statistic = "Average"
            ),
            scalingPolicy = StepScalingPolicy(
              adjustmentType = "PercentChangeInCapacity",
              cooldownSec = 120,
              metricAggregationType = "Average",
              stepAdjustments = listOf(
                StepAdjustmentModel(
                  metricIntervalUpperBound = 0.0,
                  scalingAdjustment = -3
                )
              )
            )
          )
        )
      )
    )
  )

  val cloudDriverService = mockk<CloudDriverService>() {
    every { getAccountInformation(account, any()) } returns mapOf(
      "awsAccount" to "test",
      "registry" to "testregistry"
    )

    every { findDockerImages("testregistry", "fnord/fnord", any(), any(), any(), any()) } returns listOf(
      DockerImage(
        account = "testregistry",
        repository = actualServerGroup.image.dockerImageName,
        tag = actualServerGroup.image.dockerImageVersion,
        digest = actualServerGroup.image.dockerImageDigest
      )
    )
  }

  fun CloudDriverService.stubActiveServerGroup(serverGroup: TitusActiveServerGroup) {
    every {
      titusActiveServerGroup(
        user = any(),
        app = serverGroup.moniker.app,
        account = serverGroup.awsAccount,
        cluster = serverGroup.moniker.toName(),
        region = serverGroup.region,
        cloudProvider = "titus"
      )
    } returns serverGroup

    every {
      listTitusServerGroups(
        user = any(),
        app = serverGroup.moniker.app,
        account = serverGroup.awsAccount,
        cluster = serverGroup.moniker.toName(),
        cloudProvider = "titus"
      )
    } returns ServerGroupCollection(
      accountName = serverGroup.awsAccount,
      serverGroups = setOf(serverGroup.toAllServerGroupsResponse())
    )
  }

  val stages = slot<List<Job>>()
  val taskLauncher = mockk<TaskLauncher>() {
    every {
      submitJob(any(), any(), any(), capture(stages))
    } answers {
      Task(randomUID().toString(), arg(1))
    }
  }

  val handler = TitusClusterHandler(
    cloudDriverService = cloudDriverService,
    cloudDriverCache = mockk(),
    orcaService = mockk(),
    clock = Clock.systemDefaultZone(),
    taskLauncher = taskLauncher,
    eventPublisher = mockk(relaxUnitFun = true),
    resolvers = emptyList(),
    clusterExportHelper = mockk()
  )

  val resource = resource(
    kind = TITUS_CLUSTER_V1.kind,
    spec = TitusClusterSpec(
      moniker = Moniker(application, "test"),
      locations = SimpleLocations(account = account, regions = setOf(SimpleRegionSpec(name = region))),
      container = DigestProvider(application, application, actualServerGroup.image.dockerImageDigest),
      capacity = CapacitySpec(min = 1, max = 10, desired = null),
      scaling = TitusScalingSpec(
        targetTrackingPolicies = setOf(
          TargetTrackingPolicy(
            targetValue = 35.0,
            customMetricSpec = CustomizedMetricSpecification(
              namespace = "NFLX/EPIC",
              name = "AverageCPUUtilization",
              statistic = "Average"
            )
          )
        ),
        stepScalingPolicies = setOf(
          com.netflix.spinnaker.keel.api.ec2.StepScalingPolicy(
            comparisonOperator = "LessThanOrEqualToThreshold",
            evaluationPeriods = 60,
            period = Duration.ofSeconds(60),
            threshold = 20,
            namespace = "NFLX/EPIC",
            metricName = "AverageCPUUtilization",
            statistic = "Average",
            adjustmentType = "PercentChangeInCapacity",
            // cooldownSec = 120, // TODO: need this in the model (seems to be Titus only)
            metricAggregationType = "Average",
            stepAdjustments = setOf(
              StepAdjustment(
                upperBound = 0.0,
                scalingAdjustment = -3
              )
            ),
            actionsEnabled = true // TODO: is this reflected in CloudDriver?
          )
        )
      )
    )
  )

  @Test
  fun `actual state for a Titus cluster with a target tracking policy is resolved correctly`() {
    cloudDriverService.stubActiveServerGroup(actualServerGroup)

    val current = runBlocking {
      handler.current(resource)
    }

    expectThat(current[region])
      .isNotNull()
      .get { scaling.targetTrackingPolicies }
      .isNotEmpty()
  }

  @Test
  fun `actual state for a Titus cluster with a step scaling policy is resolved correctly`() {
    cloudDriverService.stubActiveServerGroup(actualServerGroup)

    val current = runBlocking {
      handler.current(resource)
    }

    expectThat(current[region])
      .isNotNull()
      .get { scaling.stepScalingPolicies }
      .isNotEmpty()
  }

  @Test
  fun `desired state for a Titus cluster with a target tracking policy is resolved correctly`() {
    cloudDriverService.stubActiveServerGroup(actualServerGroup)

    val desired = runBlocking {
      handler.desired(resource)
    }

    expectThat(desired[region])
      .isNotNull()
      .get { scaling.targetTrackingPolicies }
      .isNotEmpty()
  }

  @Test
  fun `the AutoScalingGroupName is removed from any actual scaling policy so we don't diff it`() {
    cloudDriverService.stubActiveServerGroup(actualServerGroup)

    val current = runBlocking {
      handler.current(resource)
    }

    expectThat(current[region])
      .isNotNull()
      .get { scaling.targetTrackingPolicies }
      .isNotEmpty()
      .first()
      .get { customMetricSpec?.dimensions }
      .isNotNull()
      .doesNotContain(MetricDimension("AutoScalingGroupName", asg))
  }

  @Test
  fun `desired state for a Titus cluster with a step scaling policy is resolved correctly`() {
    cloudDriverService.stubActiveServerGroup(actualServerGroup)

    val desired = runBlocking {
      handler.desired(resource)
    }

    expectThat(desired[region])
      .isNotNull()
      .get { scaling.stepScalingPolicies }
      .isNotEmpty()
  }

  @Test
  fun `a diff that includes scaling policies triggers a task with multiple stages`() {
    cloudDriverService.stubActiveServerGroup(
      actualServerGroup.copy(
        resources = Resources(cpu = 8, memory = 8192),
        scalingPolicies = emptyList()
      )
    )

    val desired = runBlocking { handler.desired(resource) }
    val current = runBlocking { handler.current(resource) }

    runBlocking {
      handler.upsert(resource, DefaultResourceDiff(desired, current))
    }

    expectThat(stages)
      .withCaptured {
        // there are 2 upsertScalingPolicy stages, one creates the target tracking policy, the other the step scaling
        // policy deployment is handled separately as createServerGroup does not do anything with scaling policies
        map { it.type } isEqualTo listOf("createServerGroup", "upsertScalingPolicy", "upsertScalingPolicy")
      }
  }

  @Test
  fun `a diff only in scaling policies triggers a modify scaling policies task`() {
    cloudDriverService.stubActiveServerGroup(actualServerGroup.copy(scalingPolicies = emptyList()))

    val desired = runBlocking { handler.desired(resource) }
    val current = runBlocking { handler.current(resource) }

    runBlocking {
      handler.upsert(resource, DefaultResourceDiff(desired, current))
    }

    expectThat(stages)
      .withCaptured {
        // there are 2 upsertScalingPolicy stages, one creates the target tracking policy, the other the step scaling
        // policy
        map { it.type } isEqualTo listOf("upsertScalingPolicy", "upsertScalingPolicy")
      }
  }

  @Test
  fun `default scaling dimension is added to the task`() {
    cloudDriverService.stubActiveServerGroup(actualServerGroup.copy(scalingPolicies = emptyList()))

    val desired = runBlocking { handler.desired(resource) }
    val current = runBlocking { handler.current(resource) }

    runBlocking {
      handler.upsert(resource, DefaultResourceDiff(desired, current))
    }

    expectThat(stages)
      .withCaptured {
        first { it.containsKey("targetTrackingConfiguration") }
          .get("targetTrackingConfiguration")
          .isA<Map<String, Any?>>()
          .get("customizedMetricSpecification")
          .isA<CustomizedMetricSpecificationModel>()
          .get { dimensions }
          .isNotNull()
          .contains(MetricDimensionModel("AutoScalingGroupName", asg))
      }
  }
}
