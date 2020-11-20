package com.netflix.spinnaker.keel.test

import com.netflix.spinnaker.igor.ArtifactService
import com.netflix.spinnaker.keel.api.artifacts.ArtifactType
import com.netflix.spinnaker.keel.api.artifacts.DeliveryArtifact
import com.netflix.spinnaker.keel.api.artifacts.PublishedArtifact
import com.netflix.spinnaker.keel.api.artifacts.SortingStrategy
import com.netflix.spinnaker.keel.api.plugins.ArtifactSupplier
import com.netflix.spinnaker.keel.api.support.SpringEventPublisherBridge
import com.netflix.spinnaker.keel.artifacts.DebianArtifactSupplier
import com.netflix.spinnaker.keel.artifacts.DockerArtifactSupplier
import com.netflix.spinnaker.keel.artifacts.NpmArtifactSupplier
import com.netflix.spinnaker.keel.clouddriver.CloudDriverService
import com.netflix.spinnaker.keel.services.ArtifactMetadataService
import io.mockk.mockk
import org.springframework.core.env.Environment

class DummyArtifact(
  override val name: String = "fnord",
  override val deliveryConfigName: String? = "manifest",
  override val reference: String = "fnord"
) : DeliveryArtifact() {
  override val type: ArtifactType = "dummy"
  override val sortingStrategy = DummySortingStrategy
}

object DummySortingStrategy : SortingStrategy {
  override val comparator: Comparator<PublishedArtifact> = compareByDescending { it.version }
  override val type = "dummy"
}

fun defaultArtifactSuppliers(): List<ArtifactSupplier<*, *>> {
  val artifactService: ArtifactService = mockk(relaxUnitFun = true)
  val clouddriverService: CloudDriverService = mockk(relaxUnitFun = true)
  val eventBridge: SpringEventPublisherBridge = mockk(relaxUnitFun = true)
  val artifactMetadataService: ArtifactMetadataService = mockk(relaxUnitFun = true)
  val springEnv: Environment = mockk(relaxed = true)
  return listOf(
    DebianArtifactSupplier(eventBridge, artifactService, artifactMetadataService, springEnv),
    DockerArtifactSupplier(eventBridge, clouddriverService, artifactMetadataService),
    NpmArtifactSupplier(eventBridge, artifactService, artifactMetadataService)
  )
}
