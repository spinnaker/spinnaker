package com.netflix.spinnaker.keel.artifacts

import com.fasterxml.jackson.annotation.JsonIgnore
import com.netflix.spinnaker.keel.api.artifacts.ArtifactOriginFilter
import com.netflix.spinnaker.keel.api.artifacts.ArtifactStatus
import com.netflix.spinnaker.keel.api.artifacts.BranchFilter
import com.netflix.spinnaker.keel.api.artifacts.DEBIAN
import com.netflix.spinnaker.keel.api.artifacts.DeliveryArtifact
import com.netflix.spinnaker.keel.api.artifacts.SortingStrategy
import com.netflix.spinnaker.keel.api.artifacts.VirtualMachineOptions

/**
 * A [DeliveryArtifact] that describes Debian packages.
 */
data class DebianArtifact(
  override val name: String,
  override val deliveryConfigName: String? = null,
  override val reference: String = name,
  override val statuses: Set<ArtifactStatus> = emptySet(),
  val vmOptions: VirtualMachineOptions,
  @JsonIgnore val branch: String? = null,
  override val from: ArtifactOriginFilter? =
    branch?.let { ArtifactOriginFilter(BranchFilter(name = branch)) }
) : DeliveryArtifact() {
  override val type = DEBIAN

  override val sortingStrategy: SortingStrategy
    get() = if (filteredBySource) {
      CreatedAtSortingStrategy
    } else {
      DebianVersionSortingStrategy
    }

  override fun withDeliveryConfigName(deliveryConfigName: String): DeliveryArtifact {
    return this.copy(deliveryConfigName = deliveryConfigName)
  }

  override fun toString(): String = super.toString()
}
