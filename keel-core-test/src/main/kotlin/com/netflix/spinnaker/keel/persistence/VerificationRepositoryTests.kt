package com.netflix.spinnaker.keel.persistence

import com.netflix.spinnaker.keel.api.DeliveryConfig
import com.netflix.spinnaker.keel.api.Environment
import com.netflix.spinnaker.keel.api.Verification
import com.netflix.spinnaker.keel.api.constraints.ConstraintStatus
import com.netflix.spinnaker.keel.api.constraints.ConstraintStatus.FAIL
import com.netflix.spinnaker.keel.api.constraints.ConstraintStatus.OVERRIDE_PASS
import com.netflix.spinnaker.keel.api.constraints.ConstraintStatus.PASS
import com.netflix.spinnaker.keel.api.constraints.ConstraintStatus.PENDING
import com.netflix.spinnaker.keel.api.verification.VerificationContext
import com.netflix.spinnaker.keel.api.verification.VerificationRepository
import com.netflix.spinnaker.keel.api.verification.VerificationState
import com.netflix.spinnaker.keel.artifacts.DockerArtifact
import com.netflix.spinnaker.time.MutableClock
import de.huxhorn.sulky.ulid.ULID
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import strikt.api.expectCatching
import strikt.assertions.first
import strikt.assertions.get
import strikt.assertions.hasSize
import strikt.assertions.isA
import strikt.assertions.isEmpty
import strikt.assertions.isEqualTo
import strikt.assertions.isNotEmpty
import strikt.assertions.isNotNull
import strikt.assertions.isNull
import strikt.assertions.isSuccess
import strikt.assertions.one
import strikt.assertions.withFirst
import java.time.Duration

abstract class VerificationRepositoryTests<IMPLEMENTATION : VerificationRepository> {

  abstract fun createSubject(): IMPLEMENTATION

  val clock = MutableClock()
  val subject: IMPLEMENTATION by lazy { createSubject() }

  open fun VerificationContext.setup() {}
  open fun VerificationContext.setupCurrentArtifactVersion() {}
  open fun VerificationContext.pauseApplication() {}

  protected data class DummyVerification(val value: String) : Verification {
    override val type = "dummy"
    override val id: String = "$type:$value"
  }

  private val verification = DummyVerification("1")
  private val context = VerificationContext(
    deliveryConfig = DeliveryConfig(
      application = "fnord",
      name = "fnord-manifest",
      serviceAccount = "jamm@illuminati.org",
      artifacts = setOf(
        DockerArtifact(
          name = "fnord",
          deliveryConfigName = "fnord-manifest",
          reference = "fnord-docker-stable"
        ),
        DockerArtifact(
          name = "fnord",
          deliveryConfigName = "fnord-manifest",
          reference = "fnord-docker-unstable"
        )
      ),
      environments = setOf(
        Environment(
          name = "test",
          verifyWith = listOf(verification)
        )
      )
    ),
    environmentName = "test",
    artifactReference = "fnord-docker-stable",
    version = "fnord-0.190.0-h378.eacb135"
  )

  @Test
  fun `an unknown verification has a null state`() {
    context.setup()

    expectCatching {
      subject.getState(context, verification)
    }
      .isSuccess()
      .isNull()
  }

  @ParameterizedTest
  @EnumSource(ConstraintStatus::class)
  fun `after initial creation a verification state can be retrieved`(status: ConstraintStatus) {
    context.setup()

    val metadata = mapOf("tasks" to listOf(ULID().nextULID()))
    subject.updateState(context, verification, status, metadata)

    expectCatching {
      subject.getState(context, verification)
    }
      .isSuccess()
      .isNotNull()
      .with(VerificationState::status) { isEqualTo(status) }
      .with(VerificationState::metadata) { isEqualTo(metadata) }
  }

  @Test
  fun `successive updates do not wipe out metadata`() {
    context.setup()

    subject.updateState(context, verification, PENDING)
    val metadata = mapOf("tasks" to listOf(ULID().nextULID()))
    subject.updateState(context, verification, PENDING, metadata)
    subject.updateState(context, verification, PASS)

    expectCatching {
      subject.getState(context, verification)
    }
      .isSuccess()
      .isNotNull()
      .with(VerificationState::status) { isEqualTo(PASS) }
      .with(VerificationState::metadata) { isEqualTo(metadata) }
  }

  @Test
  fun `successive updates can add and update metadata`() {
    context.setup()

    subject.updateState(context, verification, PENDING)
    val initialMetadata = mapOf("tasks" to listOf(ULID().nextULID()))
    subject.updateState(context, verification, FAIL, initialMetadata)
    val newMetadata = mapOf("overriddenBy" to "flzlem@netflix.com", "comment" to "flaky test!")
    subject.updateState(context, verification, OVERRIDE_PASS, newMetadata)

    expectCatching {
      subject.getState(context, verification)
    }
      .isSuccess()
      .isNotNull()
      .with(VerificationState::status) { isEqualTo(OVERRIDE_PASS) }
      .with(VerificationState::metadata) { isEqualTo(initialMetadata + newMetadata) }
  }

  @Test
  fun `successive updates can append to arrays in the metadata`() {
    context.setup()

    subject.updateState(context, verification, PENDING)
    val initialMetadata = mapOf("tasks" to listOf(ULID().nextULID()))
    subject.updateState(context, verification, FAIL, initialMetadata)
    val newMetadata = mapOf("tasks" to listOf(ULID().nextULID()))
    subject.updateState(context, verification, PASS, newMetadata)

    expectCatching {
      subject.getState(context, verification)
    }
      .isSuccess()
      .isNotNull()
      .with(VerificationState::metadata) {
        get("tasks").isA<List<*>>().hasSize(2)
      }
  }

  @Test
  fun `different verifications are isolated from one another`() {
    val verification2 = DummyVerification("2")
    val context = context.run {
      copy(
        deliveryConfig = deliveryConfig.run {
          copy(
            environments = setOf(
              Environment(
                name = "test",
                verifyWith = listOf(verification, verification2)
              )
            )
          )
        }
      )
    }

    context.setup()

    val metadata1 = mapOf("taskId" to ULID().nextULID())
    val metadata2 = mapOf("taskId" to ULID().nextULID())

    subject.updateState(context, verification, PASS, metadata1)
    subject.updateState(context, verification2, PENDING, metadata2)

    expectCatching {
      subject.getState(context, verification)
    }
      .isSuccess()
      .isNotNull()
      .with(VerificationState::status) { isEqualTo(PASS) }
      .with(VerificationState::metadata) { isEqualTo(metadata1) }

    expectCatching {
      subject.getState(context, verification2)
    }
      .isSuccess()
      .isNotNull()
      .with(VerificationState::status) { isEqualTo(PENDING) }
      .with(VerificationState::metadata) { isEqualTo(metadata2) }
  }

  @DisplayName("selecting verifications to check")
  @Nested
  inner class NextCheckTests {
    private val minAge = Duration.ofMinutes(1)

    private fun next(limit: Int = 1) = expectCatching {
      subject.nextEnvironmentsForVerification(minAge, limit)
    }

    @Test
    fun `nothing is returned to check if there is no current artifact version for any environment`() {
      context.setup()

      next().isSuccess().isEmpty()
    }

    @Test
    fun `returns the current version if it has yet to be verified`() {
      with(context) {
        setup()
        setupCurrentArtifactVersion()
      }

      next()
        .isSuccess()
        .hasSize(1)
        .withFirst {
          get { version } isEqualTo context.version
          get { environmentName } isEqualTo context.environmentName
          get { deliveryConfig.name } isEqualTo context.deliveryConfig.name
        }
    }

    @Test
    fun `returns nothing if the application is paused`() {
      with(context) {
        setup()
        setupCurrentArtifactVersion()
        pauseApplication()
      }

      next().isSuccess().isEmpty()
    }

    @Test
    fun `subsequent calls within the cutoff time do not return the same results`() {
      with(context) {
        setup()
        setupCurrentArtifactVersion()
      }

      next().isSuccess().isNotEmpty()
      next().isSuccess().isEmpty()
    }

    @Test
    fun `a new artifact version will get checked right away`() {
      with(context) {
        setup()
        setupCurrentArtifactVersion()

        next().isSuccess().isNotEmpty().first().get { version } isEqualTo version
      }

      with(context.copy(version = "fnord-0.191.0-h379.eef6bbd")) {
        setup()
        setupCurrentArtifactVersion()

        next().isSuccess().isNotEmpty().first().get { version } isEqualTo version
      }
    }

    @Test
    fun `once the cutoff time has passed the same results may be returned`() {
      with(context) {
        setup()
        setupCurrentArtifactVersion()
      }

      next().isSuccess().isNotEmpty()
      clock.incrementBy(minAge + Duration.ofSeconds(1))
      next().isSuccess().isNotEmpty()
    }

    @Test
    fun `can return both artifacts for an environment that uses more than one`() {
      val artifact1 = DockerArtifact(
        name = "artifact1",
        deliveryConfigName = context.deliveryConfig.name,
        reference = "ref-1"
      )
      val artifact2 = DockerArtifact(
        name = "artifact2",
        deliveryConfigName = context.deliveryConfig.name,
        reference = "ref-2"
      )
      val deliveryConfig = context.deliveryConfig.copy(
        artifacts = setOf(artifact1, artifact2)
      )
      val context1 = context.copy(
        deliveryConfig = deliveryConfig,
        artifactReference = artifact1.reference,
        version = "artifact1-0.254.0-h645.4ce7392"
      )
      val context2 = context.copy(
        deliveryConfig = deliveryConfig,
        artifactReference = artifact2.reference,
        version = "artifact2-0.390.0-h584.93a3040"
      )
      with(context1) {
        setup()
        setupCurrentArtifactVersion()
      }
      with(context2) {
        setup()
        setupCurrentArtifactVersion()
      }

      next(2)
        .isSuccess()
        .hasSize(2)
        .one {
          get { artifactReference } isEqualTo context1.artifactReference
          get { version } isEqualTo context1.version
        }
        .one {
          get { artifactReference } isEqualTo context2.artifactReference
          get { version } isEqualTo context2.version
        }
    }

    @Test
    fun `can return both artifacts sequentially when an environment has more than one`() {
      val artifact1 = DockerArtifact(
        name = "artifact1",
        deliveryConfigName = context.deliveryConfig.name,
        reference = "ref-1"
      )
      val artifact2 = DockerArtifact(
        name = "artifact2",
        deliveryConfigName = context.deliveryConfig.name,
        reference = "ref-2"
      )
      val deliveryConfig = context.deliveryConfig.copy(
        artifacts = setOf(artifact1, artifact2)
      )
      val context1 = context.copy(
        deliveryConfig = deliveryConfig,
        artifactReference = artifact1.reference,
        version = "artifact1-0.254.0-h645.4ce7392"
      )
      val context2 = context.copy(
        deliveryConfig = deliveryConfig,
        artifactReference = artifact2.reference,
        version = "artifact2-0.390.0-h584.93a3040"
      )
      with(context1) {
        setup()
        setupCurrentArtifactVersion()
      }
      with(context2) {
        setup()
        setupCurrentArtifactVersion()
      }

      next().isSuccess().hasSize(1)
      next().isSuccess().hasSize(1)
    }
  }
}
