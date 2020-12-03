package com.netflix.spinnaker.keel.titus.batch

import com.netflix.spinnaker.keel.titus.batch.ImageVersionReference.*

const val RUN_JOB_TYPE : String = "runJob"

/**
 * Containers can be identified by either:
 *  tag (e.g., "latest")
 *  digest (e.g., "sha256:780f11bfc03495da29f9e2d25bf55123330715fb494ac27f45c96f808fd2d4c5")
 */
enum class ImageVersionReference {
  TAG,
  DIGEST
}

/**
 * Holds the config info needed to execute a batch job on Titus via the Run Job stage
 */
data class ContainerJobConfig(
  val name : String,
  val application: String,
  val account: String,
  val region: String,
  val organization: String,
  /**
   * Repository name associated with the container, e.g.: "acme/widget"
   */
  val repository: String,
  val serviceAccount: String,
  val credentials: String,
  val registry : String,
  val type: ImageVersionReference = TAG,
  /**
   * Either a tag (e.g., "latest") or a digest (e.g., "sha256:be93efc727ba59813adc896859bccf32fb0f02202fe0526a9cd76326b9729cb3),
   * depending on what [type] is
   */
  val versionIdentifier: String = "latest",
  /**
   * ID that identifies an image, e.g.:
   *
   * acme/widget:latest
   * acme/widget:sha256:780f11bfc03495da29f9e2d25bf55123330715fb494ac27f45c96f808fd2d4c5
   */
  val imageId: String = "$repository:$versionIdentifier",
  val desiredCapacity: Int= 1,
  val maxCapacity: Int = 1,
  val minCapacity: Int = 1,
  val cpus: Int = 2,
  val memoryMb: Int = 2048,
  val diskMb: Int = 256,
  val networkMbps: Int = 1024,
  val gpus: Int = 0,
  val entrypoint: String = "",
  val runtimeLimitSeconds: Int = 2700,
  val retries: Int = 0,
  val cloudProvider : String = "titus",
  val cloudProviderType: String = "aws",
  val iamInstanceProfile: String = application + "InstanceProfile",
  val securityGroups: List<String> = emptyList(),
  val capacityGroup: String = application,
  val containerAttributes: Map<String, String> = emptyMap(),
  val environmentVariables: Map<String, String> = emptyMap(),
  val labels: Map<String, String> = emptyMap(),
  val deferredInitialization : Boolean = true,
  val waitForCompletion: Boolean = true
) {

  /**
   * Create a Spinnaker "Run Job" stage. A Run Job stage executes a container.
   *
   * See SubmitJobRequest class in clouddriver:
   * https://github.com/spinnaker/clouddriver/blob/master/clouddriver-titus/src/main/groovy/com/netflix/spinnaker/clouddriver/titus/client/model/SubmitJobRequest.java
   */
  fun createRunJobStage() =
      mutableMapOf(
        "type" to RUN_JOB_TYPE,
        "name" to name,
        "cloudProviderType" to cloudProviderType,
        "cluster" to mapOf(
          "capacity" to mapOf(
            "min" to minCapacity,
            "max" to maxCapacity,
            "desired" to desiredCapacity
          ),
          "application" to application,
          "containerAttributes" to containerAttributes,
          "env" to environmentVariables,
          "labels" to labels,
          "resources" to mapOf(
            "cpu" to cpus,
            "disk" to diskMb,
            "gpu" to gpus,
            "memory" to memoryMb,
            "networkMbps" to networkMbps,
          ),
          "retries" to retries,
          "runtimeLimitSecs" to runtimeLimitSeconds,
          "securityGroups" to securityGroups,
          "iamProfile" to iamInstanceProfile,
          "region" to region,
          "capacityGroup" to capacityGroup,
          "imageId" to imageId,
          "entryPoint" to entrypoint
        ),

        "waitForCompletion" to waitForCompletion,
        "cloudProvider" to cloudProvider,
        "deferredInitialization" to deferredInitialization,
        "credentials" to credentials,
        "registry" to registry,
        "account" to account,
        "organization" to organization,
        "repository" to repository,
        when(type) {
          TAG -> "tag"
          DIGEST -> "digest"
        } to versionIdentifier,

        "region" to region,
    )
}

