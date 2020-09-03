package com.netflix.spinnaker.keel.test

import com.netflix.spinnaker.igor.ArtifactService
import com.netflix.spinnaker.keel.api.artifacts.ArtifactType
import com.netflix.spinnaker.keel.api.artifacts.DeliveryArtifact
import com.netflix.spinnaker.keel.api.artifacts.VersioningStrategy
import com.netflix.spinnaker.keel.api.plugins.ArtifactSupplier
import com.netflix.spinnaker.keel.api.support.SpringEventPublisherBridge
import com.netflix.spinnaker.keel.artifacts.DebianArtifactSupplier
import com.netflix.spinnaker.keel.artifacts.DockerArtifactSupplier
import com.netflix.spinnaker.keel.artifacts.NpmArtifactSupplier
import com.netflix.spinnaker.keel.clouddriver.CloudDriverService
import com.netflix.spinnaker.keel.services.ArtifactMetadataService
import io.mockk.mockk

class DummyArtifact(
  override val name: String = "fnord",
  override val deliveryConfigName: String? = "manifest",
  override val reference: String = "fnord"
) : DeliveryArtifact() {
  override val type: ArtifactType = "dummy"
  override val versioningStrategy = DummyVersioningStrategy
}

object DummyVersioningStrategy : VersioningStrategy {
  override val comparator: Comparator<String> = Comparator.naturalOrder()
  override val type = "dummy"
}

fun defaultArtifactSuppliers(): List<ArtifactSupplier<*, *>> {
  val artifactService: ArtifactService = mockk(relaxUnitFun = true)
  val clouddriverService: CloudDriverService = mockk(relaxUnitFun = true)
  val eventBridge: SpringEventPublisherBridge = mockk(relaxUnitFun = true)
  val artifactMetadataService: ArtifactMetadataService = mockk(relaxUnitFun = true)
  return listOf(
    DebianArtifactSupplier(eventBridge, artifactService, artifactMetadataService),
    DockerArtifactSupplier(eventBridge, clouddriverService, artifactMetadataService),
    NpmArtifactSupplier(eventBridge, artifactService, artifactMetadataService)
  )
}
