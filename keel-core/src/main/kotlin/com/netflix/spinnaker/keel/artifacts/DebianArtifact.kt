package com.netflix.spinnaker.keel.artifacts

import com.netflix.spinnaker.keel.api.artifacts.ArtifactStatus
import com.netflix.spinnaker.keel.api.artifacts.DEBIAN
import com.netflix.spinnaker.keel.api.artifacts.DeliveryArtifact
import com.netflix.spinnaker.keel.api.artifacts.VersioningStrategy
import com.netflix.spinnaker.keel.api.artifacts.VirtualMachineOptions

/**
 * A [DeliveryArtifact] that describes Debian packages.
 */
data class DebianArtifact(
  override val name: String,
  override val deliveryConfigName: String? = null,
  override val reference: String = name,
  val vmOptions: VirtualMachineOptions,
  override val statuses: Set<ArtifactStatus> = emptySet(),
  override val versioningStrategy: VersioningStrategy = DebianVersioningStrategy
) : DeliveryArtifact() {
  override val type = DEBIAN
  override fun toString(): String = super.toString()
}
