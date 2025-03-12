package com.netflix.spinnaker.keel.lifecycle

import com.netflix.spinnaker.keel.api.Environment
import com.netflix.spinnaker.keel.api.Resource
import com.netflix.spinnaker.keel.api.ResourceKind
import com.netflix.spinnaker.keel.api.ResourceKind.Companion.parseKind
import com.netflix.spinnaker.keel.api.artifacts.ArtifactOriginFilter
import com.netflix.spinnaker.keel.api.artifacts.BranchFilter
import com.netflix.spinnaker.keel.api.artifacts.DOCKER
import com.netflix.spinnaker.keel.api.plugins.kind
import com.netflix.spinnaker.keel.artifacts.DockerArtifact
import com.netflix.spinnaker.keel.lifecycle.LifecycleEventScope.PRE_DEPLOYMENT
import com.netflix.spinnaker.keel.lifecycle.LifecycleEventStatus.SUCCEEDED
import com.netflix.spinnaker.keel.lifecycle.LifecycleEventType.BAKE
import com.netflix.spinnaker.keel.lifecycle.LifecycleEventType.BUILD
import com.netflix.spinnaker.keel.persistence.DeliveryConfigRepository
import com.netflix.spinnaker.keel.persistence.NoSuchDeliveryConfigException
import com.netflix.spinnaker.keel.persistence.NoSuchDeliveryConfigName
import com.netflix.spinnaker.keel.test.DummyArtifactReferenceResourceSpec
import com.netflix.spinnaker.keel.test.DummyResourceSpec
import com.netflix.spinnaker.keel.test.deliveryConfig
import com.netflix.spinnaker.keel.test.resource
import io.mockk.Called
import io.mockk.mockk
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import org.junit.jupiter.params.provider.EnumSource.Mode.EXCLUDE
import strikt.api.expectCatching
import strikt.assertions.isSuccess
import io.mockk.coEvery as every
import io.mockk.coVerify as verify

internal class EnvironmentVersionForArtifactVersionTriggerTests {

  private val deliveryConfigName = "fnord-manifest"
  private val artifactRef = "fnord-ref"
  private val repository = mockk<DeliveryConfigRepository>(relaxUnitFun = true)
  private val subject = EnvironmentVersionForArtifactVersionTrigger(repository)
  private val event = LifecycleEvent(
    scope = PRE_DEPLOYMENT,
    deliveryConfigName = deliveryConfigName,
    artifactReference = artifactRef,
    artifactVersion = "fnord-1",
    type = BUILD,
    id = "id",
    status = SUCCEEDED,
    text = "build succeeded"
  )

  @ParameterizedTest(name = "does nothing for bake events with {arguments} status")
  @EnumSource(LifecycleEventStatus::class)
  fun `does nothing for bake events`(status: LifecycleEventStatus) {
    subject.onLifecycleEvent(
      event.copy(
        type = BAKE,
        status = status
      )
    )

    verify { repository wasNot Called }
  }

  @ParameterizedTest(name = "does nothing for build events with {arguments} status")
  @EnumSource(LifecycleEventStatus::class, mode = EXCLUDE, names = ["SUCCEEDED"])
  fun `does nothing for build events that are not success`(status: LifecycleEventStatus) {
    subject.onLifecycleEvent(
      event.copy(
        type = BUILD,
        status = status
      )
    )

    verify { repository wasNot Called }
  }

  @Test
  fun `creates an environment version when the build is for an artifact used in that environment`() {
    val artifact = DockerArtifact(
      name = "fnord",
      deliveryConfigName = deliveryConfigName,
      reference = artifactRef,
      from = ArtifactOriginFilter(BranchFilter("main"))
    )
    val environment = Environment(
      name = "whatever",
      resources = setOf(
        resource(
          spec = DummyArtifactReferenceResourceSpec(
            artifactType = DOCKER,
            artifactReference = artifactRef
          )
        )
      )
    )
    val deliveryConfig = deliveryConfig(
      artifact = artifact,
      env = environment
    )

    every { repository.get(deliveryConfigName) } returns deliveryConfig

    subject.onLifecycleEvent(event)

    verify {
      repository.addArtifactVersionToEnvironment(
        deliveryConfig,
        environment.name,
        artifact,
        event.artifactVersion
      )
    }
  }

  @Test
  fun `does nothing for environments that do not use the artifact whose build succeeded`() {
    val artifact = DockerArtifact(
      name = "fnord",
      deliveryConfigName = deliveryConfigName,
      reference = artifactRef,
      from = ArtifactOriginFilter(BranchFilter("main"))
    )
    val environment = Environment(
      name = "whatever",
      resources = setOf(
        resource(
          spec = DummyResourceSpec()
        )
      )
    )
    val deliveryConfig = deliveryConfig(
      artifact = artifact,
      env = environment
    )

    every { repository.get(deliveryConfigName) } returns deliveryConfig

    subject.onLifecycleEvent(event)

    verify(exactly = 0) {
      repository.addArtifactVersionToEnvironment(any(), any(), any(), any())
    }
  }

  @Test
  fun `ignores events for unidentifiable delivery configs`() {
    every { repository.get(deliveryConfigName) } throws NoSuchDeliveryConfigName(deliveryConfigName)

    expectCatching { subject.onLifecycleEvent(event) }
      .isSuccess()

    verify(exactly = 0) {
      repository.addArtifactVersionToEnvironment(any(), any(), any(), any())
    }
  }

  @Test
  fun `ignores events for artifacts not found in the delivery config`() {
    val artifact = DockerArtifact(
      name = "fnord",
      deliveryConfigName = deliveryConfigName,
      reference = "some-other-artifact-reference",
      from = ArtifactOriginFilter(BranchFilter("main"))
    )
    val environment = Environment(
      name = "whatever",
      resources = setOf(
        resource(
          spec = DummyArtifactReferenceResourceSpec(
            artifactType = DOCKER,
            artifactReference = artifact.reference
          )
        )
      )
    )
    val deliveryConfig = deliveryConfig(
      artifact = artifact,
      env = environment
    )

    every { repository.get(deliveryConfigName) } returns deliveryConfig

    expectCatching { subject.onLifecycleEvent(event) }
      .isSuccess()

    verify(exactly = 0) {
      repository.addArtifactVersionToEnvironment(any(), any(), any(), any())
    }
  }
}
