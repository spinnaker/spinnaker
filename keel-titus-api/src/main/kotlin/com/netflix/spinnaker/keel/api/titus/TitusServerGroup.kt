package com.netflix.spinnaker.keel.api.titus

import com.netflix.spinnaker.keel.api.ExcludedFromDiff
import com.netflix.spinnaker.keel.api.VersionedArtifactProvider
import com.netflix.spinnaker.keel.api.artifacts.ArtifactType
import com.netflix.spinnaker.keel.api.artifacts.DOCKER
import com.netflix.spinnaker.keel.api.ec2.Capacity
import com.netflix.spinnaker.keel.api.ec2.ClusterDependencies
import com.netflix.spinnaker.keel.api.ec2.ServerGroup.InstanceCounts
import com.netflix.spinnaker.keel.docker.DigestProvider

data class TitusServerGroup(
  /**
   * This field is immutable, so we would never be reacting to a diff on it. If the name differs,
   * it's a different resource. Also, a server group name retrieved from CloudDriver will include
   * the sequence number. However, when we resolve desired state from a [ClusterSpec] this field
   * will _not_ include the sequence number. Having it on the model returned from CloudDriver is
   * useful for some things (e.g. specifying ancestor server group when red-blacking a new version)
   * but is meaningless for a diff.
   */
  @get:ExcludedFromDiff
  val name: String,
  val container: DigestProvider,
  val location: Location,
  val env: Map<String, String> = emptyMap(),
  val containerAttributes: Map<String, String> = emptyMap(),
  val resources: Resources = Resources(),
  val iamProfile: String,
  val entryPoint: String = "",
  val capacityGroup: String,
  val constraints: Constraints = Constraints(),
  val migrationPolicy: MigrationPolicy = MigrationPolicy(),
  val capacity: Capacity,
  val tags: Map<String, String> = emptyMap(),
  val dependencies: ClusterDependencies = ClusterDependencies(),
  val deferredInitialization: Boolean = true,
  val delayBeforeDisableSec: Int = 0,
  val delayBeforeScaleDownSec: Int = 0,
  val onlyEnabledServerGroup: Boolean = true,
  @get:ExcludedFromDiff
  override val artifactName: String? = null,
  @get:ExcludedFromDiff
  override val artifactType: ArtifactType? = DOCKER,
  @get:ExcludedFromDiff
  override val artifactVersion: String? = null,
  @get:ExcludedFromDiff
  val instanceCounts: InstanceCounts? = null
) : VersionedArtifactProvider {

  // todo eb: should this be more general?
  data class Location(
    val account: String,
    val region: String
  )

  data class Resources(
    val cpu: Int = 1,
    val disk: Int = 10000,
    val gpu: Int = 0,
    val memory: Int = 512,
    val networkMbps: Int = 128
  )

  data class Constraints(
    val hard: Map<String, Any> = emptyMap(),
    val soft: Map<String, Any> = mapOf("ZoneBalance" to "true")
  )

  data class MigrationPolicy(
    val type: String = "systemDefault"
  )
}
