package com.netflix.spinnaker.keel.api.ec2.constraints

import com.netflix.frigga.ami.AppVersion
import com.netflix.spinnaker.keel.api.CanaryConstraint
import com.netflix.spinnaker.keel.api.CanarySource
import com.netflix.spinnaker.keel.api.Capacity
import com.netflix.spinnaker.keel.api.DeliveryConfig
import com.netflix.spinnaker.keel.api.Environment
import com.netflix.spinnaker.keel.api.ec2.TerminationPolicy
import com.netflix.spinnaker.keel.api.randomUID
import com.netflix.spinnaker.keel.clouddriver.CloudDriverCache
import com.netflix.spinnaker.keel.clouddriver.CloudDriverService
import com.netflix.spinnaker.keel.clouddriver.ImageService
import com.netflix.spinnaker.keel.clouddriver.model.ActiveServerGroup
import com.netflix.spinnaker.keel.clouddriver.model.ActiveServerGroupImage
import com.netflix.spinnaker.keel.clouddriver.model.AutoScalingGroup
import com.netflix.spinnaker.keel.clouddriver.model.Credential
import com.netflix.spinnaker.keel.clouddriver.model.InstanceMonitoring
import com.netflix.spinnaker.keel.clouddriver.model.LaunchConfig
import com.netflix.spinnaker.keel.clouddriver.model.NamedImage
import com.netflix.spinnaker.keel.clouddriver.model.Subnet
import com.netflix.spinnaker.keel.clouddriver.model.SuspendedProcess
import com.netflix.spinnaker.keel.constraints.CanaryConstraintConfigurationProperties
import com.netflix.spinnaker.keel.ec2.constraints.Ec2CanaryConstraintDeployHandler
import com.netflix.spinnaker.keel.ec2.resolvers.ImageResolver
import com.netflix.spinnaker.keel.events.Task
import com.netflix.spinnaker.keel.model.parseMoniker
import com.netflix.spinnaker.keel.plugin.TaskLauncher
import dev.minutest.junit.JUnit5Minutests
import dev.minutest.rootContext
import io.mockk.CapturingSlot
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import java.time.Clock
import java.time.Duration
import kotlinx.coroutines.runBlocking
import strikt.api.expectThat
import strikt.assertions.containsExactlyInAnyOrder
import strikt.assertions.getValue
import strikt.assertions.hasSize
import strikt.assertions.isA
import strikt.assertions.isEqualTo

internal class Ec2CanaryConstraintDeployHandlerTests : JUnit5Minutests {

  companion object {
    val clock: Clock = Clock.systemUTC()
    val taskLauncher: TaskLauncher = mockk()
    val cloudDriverService: CloudDriverService = mockk()
    val cloudDriverCache: CloudDriverCache = mockk()
    val imageService: ImageService = mockk()
    val imageResolver: ImageResolver = mockk()
  }

  data class Fixture(
    val constraint: CanaryConstraint,
    val version: String,
    val deliveryConfig: DeliveryConfig,
    val targetEnvironment: Environment,
    val regions: Set<String>
  ) {
    constructor() : this(
      constraint = CanaryConstraint(
        canaryConfigId = randomUID().toString(),
        lifetime = Duration.ofMinutes(60),
        marginalScore = 75,
        passScore = 90,
        source = CanarySource(
          account = "test",
          cloudProvider = "aws",
          cluster = "fnord-prod"
        ),
        regions = setOf("us-west-1", "us-west-2"),
        capacity = 1
      ),
      version = "fnord-42.0.0-h42",
      deliveryConfig = DeliveryConfig(
        name = "fnord-manifest",
        application = "fnord",
        serviceAccount = "keel@spinnaker"
      ),
      targetEnvironment = Environment("prod"),
      regions = setOf("us-west-1", "us-west-2")
    )

    val subject = Ec2CanaryConstraintDeployHandler(
      CanaryConstraintConfigurationProperties(),
      taskLauncher,
      cloudDriverService,
      cloudDriverCache,
      imageService,
      imageResolver)
  }

  fun tests() = rootContext<Fixture> {
    fixture { Fixture() }

    context("need to launch canaries in two regions") {
      before {
        coEvery {
          imageService.getLatestNamedImageWithAllRegionsForAppVersion(any(), any(), any())
        } returns NamedImage("fnord-42.0.0-h42", emptyMap(), emptyMap(), emptySet(), emptyMap())

        coEvery {
          cloudDriverService.activeServerGroup(any(), any(), any(), any(), "us-west-1", any())
        } returns sourceServerGroup("us-west-1")

        coEvery {
          cloudDriverService.activeServerGroup(any(), any(), any(), any(), "us-west-2", any())
        } returns sourceServerGroup("us-west-2")

        coEvery {
          taskLauncher.submitJob(any(), any(), any(), any(), any(), any(), any())
        } returns Task(randomUID().toString(), "fnord canary")

        coEvery {
          imageResolver.defaultImageAccount
        } returns "test"

        coEvery {
          cloudDriverCache.subnetBy(any())
        } returns Subnet(
          id = "subnet-42",
          account = "test",
          region = "us-west-1",
          availabilityZone = "us-west-1a",
          vpcId = "vpc-123",
          purpose = "internal"
        )

        coEvery {
          cloudDriverCache.credentialBy("test")
        } returns Credential(
          name = "test",
          type = "aws",
          attributes = mapOf(
            "attributes" to mapOf(
              "environment" to "test")))
      }

      test("generates and submits an orca task per regions") {
        val slot: CapturingSlot<List<Map<String, Any>>> = slot()
        val tasks = runBlocking {
          subject.deployCanary(constraint, version, deliveryConfig, targetEnvironment, regions)
        }

        coVerify(exactly = 1) {
          imageService.getLatestNamedImageWithAllRegionsForAppVersion(AppVersion.parseName(version), "test", any())
          cloudDriverService.activeServerGroup(any(), any(), any(), any(), "us-west-1", any())
          cloudDriverService.activeServerGroup(any(), any(), any(), any(), "us-west-2", any())
        }
        coVerify(exactly = 2) {
          taskLauncher.submitJob(any(), any(), any(), any(), any(), any(), capture(slot))
        }

        expectThat(tasks)
          .hasSize(2)
          .get { keys }
          .containsExactlyInAnyOrder("us-west-1", "us-west-2")

        expectThat(slot.captured)
          .hasSize(1)
          .and {
            get { first() }
              .and {
                getValue("type").isEqualTo("kayentaCanary")
                getValue("refId").isEqualTo("canary")
                getValue("canaryConfig")
                  .isA<Map<String, Any?>>()
                  .and {
                    getValue("canaryConfigId")
                      .isEqualTo(constraint.canaryConfigId)
                    getValue("canaryAnalysisIntervalMins")
                      .isEqualTo(constraint.canaryAnalysisInterval.toMinutes())
                  }
                getValue("deployments")
                  .isA<Map<String, Any?>>()
                  .and {
                    getValue("serverGroupPairs")
                      .isA<List<Map<String, Any?>>>()
                      .hasSize(1)
                      .and {
                        get { first() }
                          .and {
                            hasSize(2)
                            getValue("experiment")
                              .isA<Map<String, Any?>>()
                              .and {
                                getValue("application")
                                  .isEqualTo(deliveryConfig.application)
                                getValue("freeFormDetails")
                                  .isEqualTo(
                                    "${parseMoniker(constraint.source.cluster).detail}-canary")
                              }
                          }
                      }
                  }
              }
          }
      }
    }
  }

  private fun sourceServerGroup(region: String) = ActiveServerGroup(
    name = "fnord-prod",
    region = region,
    zones = emptySet(),
    image = ActiveServerGroupImage("ami-42b", null, null),
    launchConfig = LaunchConfig(
      ramdiskId = null,
      ebsOptimized = true,
      imageId = "ami-42b",
      instanceType = "m5.2xlarge",
      keyName = "nosecrets",
      iamInstanceProfile = "fnordInstanceProfile",
      instanceMonitoring = InstanceMonitoring(false)
    ),
    asg = AutoScalingGroup(
      autoScalingGroupName = "fnord-prod-v042",
      defaultCooldown = 300,
      healthCheckType = "ec2",
      healthCheckGracePeriod = 300,
      suspendedProcesses = setOf(SuspendedProcess(processName = "AZRebalance")),
      enabledMetrics = emptySet(),
      tags = emptySet(),
      terminationPolicies = setOf(TerminationPolicy.OldestInstance.toString()),
      vpczoneIdentifier = "subnet-42,subnet-123"
    ),
    scalingPolicies = emptyList(),
    vpcId = "vpc-123",
    targetGroups = emptySet(),
    loadBalancers = emptySet(),
    capacity = Capacity(10, 10, 10),
    cloudProvider = "aws",
    securityGroups = setOf("fnord"),
    accountName = "test",
    moniker = parseMoniker("fnord-prod")
  )
}
