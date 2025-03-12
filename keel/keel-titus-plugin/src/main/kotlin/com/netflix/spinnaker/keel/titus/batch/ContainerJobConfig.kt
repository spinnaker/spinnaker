package com.netflix.spinnaker.keel.titus.batch

import com.netflix.spinnaker.keel.api.ec2.Capacity
import com.netflix.spinnaker.keel.api.ec2.Capacity.DefaultCapacity
import com.netflix.spinnaker.keel.api.titus.TitusServerGroup
import java.time.Duration

const val RUN_JOB_TYPE: String = "runJob"

/**
 * Holds the config info needed to execute a batch job on Titus via the Run Job stage
 */
data class ContainerJobConfig(
  /**
   * image name, with optional tag. Examples:
   *
   *   acme/widget
   *   acme/widget:latest
   */
  val image: String,
  val application: String,
  val location: TitusServerGroup.Location,
  val resources: TitusServerGroup.Resources = TitusServerGroup.Resources(
    cpu = 2,
    memory = 4096,
    disk = 256,
    networkMbps = 1024
  ),
  val capacity: Capacity = DefaultCapacity(min = 1, max = 1, desired = 1),
  val credentials: String,
  val entrypoint: String = "",
  val runtimeLimit: Duration = Duration.ofSeconds(2700),
  val retries: Int = 0,
  val iamInstanceProfile: String = application + "InstanceProfile",
  val securityGroups: List<String> = emptyList(),
  val capacityGroup: String = application,
  val containerAttributes: Map<String, String> = emptyMap(),
  val environmentVariables: Map<String, String> = emptyMap(),
  val labels: Map<String, String> = emptyMap(),
  val deferredInitialization: Boolean = true,
  val waitForCompletion: Boolean = true
) {
  init {
    require(retries >= 0) {
      "Retries must be positive or zero"
    }
  }

  val cloudProvider: String = "titus"
  val cloudProviderType: String = "aws"
}

/**
 * Create a Spinnaker "Run Job" stage. A Run Job stage executes a container.
 *
 * See SubmitJobRequest class in clouddriver:
 * https://github.com/spinnaker/clouddriver/blob/master/clouddriver-titus/src/main/groovy/com/netflix/spinnaker/clouddriver/titus/client/model/SubmitJobRequest.java
 */
fun ContainerJobConfig.createRunJobStage() =
  mutableMapOf(
    "type" to RUN_JOB_TYPE,
    "cloudProviderType" to cloudProviderType,
    "cluster" to mapOf(
      "capacity" to with(capacity) {
        mapOf(
          "min" to min,
          "max" to max,
          "desired" to desired
        )
      },
      "application" to application,
      "containerAttributes" to containerAttributes,
      "env" to environmentVariables,
      "labels" to labels,
      "resources" to with(resources) {
        mapOf(
          "cpu" to cpu,
          "disk" to disk,
          "gpu" to gpu,
          "memory" to memory,
          "networkMbps" to networkMbps,
        )
      },
      "retries" to retries,
      "runtimeLimitSecs" to runtimeLimit.seconds,
      "securityGroups" to securityGroups,
      "iamProfile" to iamInstanceProfile,
      "region" to location.region,
      "capacityGroup" to capacityGroup,
      "imageId" to image,
      "entryPoint" to entrypoint
    ),

    "waitForCompletion" to waitForCompletion,
    "cloudProvider" to cloudProvider,
    "deferredInitialization" to deferredInitialization,
    "credentials" to credentials,
    "account" to location.account,
    "region" to location.region,
  )
