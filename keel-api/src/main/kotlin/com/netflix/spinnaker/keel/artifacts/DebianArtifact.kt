package com.netflix.spinnaker.keel.artifacts

import com.netflix.spinnaker.keel.api.artifacts.ArtifactStatus
import com.netflix.spinnaker.keel.api.artifacts.ArtifactType
import com.netflix.spinnaker.keel.api.artifacts.DebianSemVerVersioningStrategy
import com.netflix.spinnaker.keel.api.artifacts.DeliveryArtifact
import com.netflix.spinnaker.keel.api.artifacts.VersioningStrategy
import com.netflix.spinnaker.keel.api.artifacts.VirtualMachineOptions

data class DebianArtifact(
  override val name: String,
  override val deliveryConfigName: String? = null,
  override val reference: String = name,
  val vmOptions: VirtualMachineOptions,
  val statuses: Set<ArtifactStatus> = emptySet(),
  override val versioningStrategy: VersioningStrategy = DebianSemVerVersioningStrategy
) : DeliveryArtifact() {
  override val type = ArtifactType.deb
}
