package com.netflix.spinnaker.keel.constraints

import com.netflix.spinnaker.keel.api.DeliveryConfig
import com.netflix.spinnaker.keel.api.Environment
import com.netflix.spinnaker.keel.api.artifacts.VirtualMachineOptions
import com.netflix.spinnaker.keel.api.constraints.ConstraintStatus
import com.netflix.spinnaker.keel.api.action.ActionRepository
import com.netflix.spinnaker.keel.api.action.ActionType.VERIFICATION
import com.netflix.spinnaker.keel.artifacts.DebianArtifact
import com.netflix.spinnaker.keel.core.api.DependsOnConstraint
import com.netflix.spinnaker.keel.persistence.ArtifactRepository
import com.netflix.spinnaker.keel.serialization.configuredObjectMapper
import com.netflix.spinnaker.time.MutableClock
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import strikt.api.expectCatching
import strikt.api.expectThat
import strikt.assertions.contains
import strikt.assertions.isA
import strikt.assertions.isEqualTo
import strikt.assertions.isFailure
import strikt.assertions.isFalse
import strikt.assertions.isNotNull
import strikt.assertions.isTrue

internal class DependsOnConstraintEvaluatorTests {

  private val artifact = DebianArtifact("fnord", vmOptions = VirtualMachineOptions(baseOs = "bionic", regions = setOf("us-west-2")))
  private val constrainedEnvironment = Environment(
    name = "staging",
    constraints = setOf(
      DependsOnConstraint(environment = "test")
    )
  )
  private val previousEnvironment = Environment(
    name = "test"
  )
  private val manifest = DeliveryConfig(
    name = "my-manifest",
    application = "fnord",
    serviceAccount = "keel@spinnaker",
    artifacts = setOf(artifact),
    environments = setOf(previousEnvironment, constrainedEnvironment)
  )

  private val artifactRepository: ArtifactRepository = mockk(relaxUnitFun = true)
  private val actionRepository: ActionRepository = mockk() {
    every { allPassed(any(), any()) } returns true
    every { allStarted(any(), any()) } returns true
  }
  private val clock = MutableClock()

  private val subject = DependsOnConstraintEvaluator(artifactRepository, actionRepository, mockk(), clock)

  @BeforeEach
  fun setup() {
    every {
      actionRepository.getStates(any(), VERIFICATION)
    } returns emptyMap()
  }

  @Test
  fun `an invalid environment name causes an exception`() {
    expectCatching {
      subject.canPromote(artifact, "1.1", manifest, Environment(name = "foo"))
    }
      .isFailure()
      .isA<IllegalArgumentException>()
  }

  @Test
  fun `an environment without the constraint throws an exception (don't pass it to this method)`() {
    expectCatching { subject.canPromote(artifact, "1.1", manifest, previousEnvironment) }
      .isFailure()
  }

  @Test
  fun `constraint serializes with type information`() {
    val mapper = configuredObjectMapper()
    val serialized = mapper.writeValueAsString(constrainedEnvironment.constraints)
    expectThat(serialized)
      .contains("depends-on")
  }

  @Nested
  inner class RequestedVersionIsNotInRequiredEnvironment {
    @BeforeEach
    fun setup() {
      every {
        artifactRepository.wasSuccessfullyDeployedTo(manifest, artifact, "1.1", previousEnvironment.name)
      } returns false
    }

    @Test
    fun `promotion is vetoed`() {
      expectThat(subject.canPromote(artifact, "1.1", manifest, constrainedEnvironment))
        .isFalse()
    }
  }

  @Nested
  inner class RequestedVersionIsInRequiredEnvironment {
    @BeforeEach
    fun setup() {
      every {
        artifactRepository.wasSuccessfullyDeployedTo(manifest, artifact, "1.1", previousEnvironment.name)
      } returns true
    }

    @Test
    fun `promotion is allowed`() {
      expectThat(subject.canPromote(artifact, "1.1", manifest, constrainedEnvironment))
        .isTrue()
    }
  }

  @Nested
  inner class GeneratingConstraintState {
    @BeforeEach
    fun setup() {
      every {
        artifactRepository.wasSuccessfullyDeployedTo(manifest, artifact, "1.1", previousEnvironment.name)
      } returns true
    }

    @Test
    fun `can get state`() {
      val state = subject.generateConstraintStateSnapshot(artifact = artifact, version = "1.1", deliveryConfig = manifest, targetEnvironment = constrainedEnvironment)
      expectThat(state)
        .and { get { type }.isEqualTo("depends-on") }
        .and { get { status }.isEqualTo(ConstraintStatus.PASS) }
        .and { get { judgedAt }.isNotNull() }
        .and { get { judgedBy }.isNotNull() }
        .and { get { attributes }.isNotNull() }
    }
  }
}
