package com.netflix.spinnaker.keel.persistence

import com.netflix.spinnaker.keel.api.DeliveryConfig
import com.netflix.spinnaker.keel.api.Environment
import com.netflix.spinnaker.keel.api.Verification
import com.netflix.spinnaker.keel.api.constraints.ConstraintStatus
import com.netflix.spinnaker.keel.api.constraints.ConstraintStatus.FAIL
import com.netflix.spinnaker.keel.api.constraints.ConstraintStatus.OVERRIDE_PASS
import com.netflix.spinnaker.keel.api.constraints.ConstraintStatus.PASS
import com.netflix.spinnaker.keel.api.constraints.ConstraintStatus.PENDING
import com.netflix.spinnaker.keel.api.ArtifactInEnvironmentContext
import com.netflix.spinnaker.keel.api.action.ActionRepository
import com.netflix.spinnaker.keel.api.action.ActionState
import com.netflix.spinnaker.keel.api.action.ActionType.VERIFICATION
import com.netflix.spinnaker.keel.api.constraints.ConstraintStatus.NOT_EVALUATED
import com.netflix.spinnaker.keel.api.postdeploy.PostDeployAction
import com.netflix.spinnaker.keel.artifacts.DockerArtifact
import com.netflix.spinnaker.time.MutableClock
import de.huxhorn.sulky.ulid.ULID
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import strikt.api.expect
import strikt.api.expectCatching
import strikt.api.expectThat
import strikt.assertions.contains
import strikt.assertions.containsExactly
import strikt.assertions.containsKey
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

abstract class ActionRepositoryTests<IMPLEMENTATION : ActionRepository> {

  abstract fun createSubject(): IMPLEMENTATION

  val clock = MutableClock()
  val subject: IMPLEMENTATION by lazy { createSubject() }

  open fun ArtifactInEnvironmentContext.setup() {}
  open fun ArtifactInEnvironmentContext.setupCurrentArtifactVersion() {}
  open fun ArtifactInEnvironmentContext.pauseApplication() {}

  protected data class DummyVerification(val value: String) : Verification {
    override val type = "dummy"
    override val id = "$type:$value"
  }

  protected data class DummyPostDeployAction(val value: String): PostDeployAction() {
    override val type = "dummy"
    override val id = "$type:$value"
  }

  private val verification = DummyVerification("1")
  private val postDeployAction = DummyPostDeployAction("2")
  private val environment = Environment(name = "test", verifyWith = listOf(verification))
  private val deliveryConfig = DeliveryConfig(
    application = "fnord",
    name = "fnord-manifest",
    serviceAccount = "jamm@illuminati.org",
    artifacts = setOf(
      DockerArtifact(
        name = "fnord",
        deliveryConfigName = "fnord-manifest",
        reference = "fnord-docker-stable",
        branch = "main"
      ),
      DockerArtifact(
        name = "fnord",
        deliveryConfigName = "fnord-manifest",
        reference = "fnord-docker-unstable",
        branch = "main"
      ),
      DockerArtifact(
        name = "fnord",
        deliveryConfigName = "fnord-manifest",
        reference = "test", // an artifact that has a reference name the same as an environment name
        branch = "main"
      )
    ),
    environments = setOf(environment)
  )
  private val context = ArtifactInEnvironmentContext(
    deliveryConfig = deliveryConfig,
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
      .with(ActionState::status) { isEqualTo(status) }
      .with(ActionState::metadata) { isEqualTo(metadata) }
      .with(ActionState::link) { isNull() }
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
      .with(ActionState::status) { isEqualTo(PASS) }
      .with(ActionState::metadata) { isEqualTo(metadata) }
  }

  @Test
  fun `reset the action clears everything`() {
    context.setup()

    subject.updateState(context, verification, PENDING)
    subject.updateState(context, verification, PASS)
    subject.resetState(context, verification, "GreatUser")

    expectCatching {
      subject.getState(context, verification)
    }
      .isSuccess()
      .isNotNull()
      .with(ActionState::status) { isEqualTo(NOT_EVALUATED) }
      .with(ActionState::endedAt) { isNull() }
      .with(ActionState::link) { isNull() }
      .with(ActionState::metadata) { isEqualTo(mapOf("retryRequestedBy" to "GreatUser")) }
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
      .with(ActionState::status) { isEqualTo(OVERRIDE_PASS) }
      .with(ActionState::metadata) { isEqualTo(initialMetadata + newMetadata) }
  }

  @Test
  fun `updates set the link`() {
    context.setup()

    subject.updateState(context, verification, PENDING, link = "http://www.example.com")

    expectCatching {
      subject.getState(context, verification)
    }
      .isSuccess()
      .isNotNull()
      .with(ActionState::link) { isEqualTo("http://www.example.com") }
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
      .with(ActionState::metadata) {
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
      .with(ActionState::status) { isEqualTo(PASS) }
      .with(ActionState::metadata) { isEqualTo(metadata1) }

    expectCatching {
      subject.getState(context, verification2)
    }
      .isSuccess()
      .isNotNull()
      .with(ActionState::status) { isEqualTo(PENDING) }
      .with(ActionState::metadata) { isEqualTo(metadata2) }
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
        reference = "ref-1",
        branch = "main"
      )
      val artifact2 = DockerArtifact(
        name = "artifact2",
        deliveryConfigName = context.deliveryConfig.name,
        reference = "ref-2",
        branch = "main"
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
        reference = "ref-1",
        branch = "main"
      )
      val artifact2 = DockerArtifact(
        name = "artifact2",
        deliveryConfigName = context.deliveryConfig.name,
        reference = "ref-2",
        branch = "main"
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

  //
  // getStatesBatch tests
  //
  @Test
  fun `no verification states`() {
    context.setup()
    val contexts = listOf(context)

    expectThat(subject.getStatesBatch(contexts, VERIFICATION))
      .hasSize(contexts.size)
      .containsExactly(emptyMap())
  }

  @Test
  fun `one verification state`() {
    context.setup()
    val contexts = listOf(context)

    subject.updateState(context, verification, PENDING, link = "http://www.example.com")

    val stateMaps = subject.getStatesBatch(contexts, VERIFICATION)

    expectThat(stateMaps)
      .hasSize(contexts.size)

    val stateMap = stateMaps.first()
    expectThat(stateMap)
      .containsKey(verification.id)

    expectThat(stateMap[verification.id])
      .isNotNull()
      .with(ActionState::status) { isEqualTo(PENDING) }
      .with(ActionState::link) { isEqualTo("http://www.example.com") }
  }

  @Test
  fun `multiple contexts, multiple verifications`() {
    val c1 = context
    val c2 = ArtifactInEnvironmentContext(
      deliveryConfig = deliveryConfig,
      environmentName = "test",
      artifactReference = "fnord-docker-stable",
      version = "fnord-0.191.0-h379.fbde246"
    )

    val v1 = verification
    val v2 = DummyVerification("2")

    val contexts = listOf(c1, c2)
    contexts.forEach { it.setup() }

    // First context: v1: PASS, v2: FAIL
    subject.updateState(c1, v1, PASS, link = "http://www.example.com/pass")
    subject.updateState(c1, v2, FAIL)

    // Second context: v1: PENDING, v2: PENDING
    subject.updateState(c2, v1, PENDING, link = "http://www.example.com/pending")
    subject.updateState(c2, v2, PENDING)


    val result = subject.getStatesBatch(contexts, VERIFICATION)

    expect {
      that(result)
        .hasSize(contexts.size)

      that(result[0])
        .and {
          get { get(v1.id) }
            .isNotNull()
            .with(ActionState::status) { isEqualTo(PASS) }
            .with(ActionState::link) { isEqualTo("http://www.example.com/pass") }
        }
        .and {
          get { get(v2.id) }
            .isNotNull()
            .with(ActionState::status) { isEqualTo(FAIL) }
            .with(ActionState::link) { isNull() }
        }

      that(result[1])
        .and {
          get { get(v1.id) }
            .isNotNull()
            .with(ActionState::status) { isEqualTo(PENDING) }
            .with(ActionState::link) { isEqualTo("http://www.example.com/pending") }
        }
        .and {
          get { get(v2.id) }
            .isNotNull()
            .get(ActionState::status) isEqualTo PENDING
        }
    }
  }

  @Test
  fun `environment name and artifact reference name are the same`() {
    val contexts = listOf(context, context.copy(artifactReference = "test"))
      .onEach { it.setup() }

    // verify it doesn't explode with a SQLSyntaxErrorException
    expectCatching { subject.getStatesBatch(contexts, VERIFICATION) }.isSuccess()
  }

  @Test
  fun `zero states`() {
    context.setup()

    val stateMaps = subject.getStatesBatch(emptyList(), VERIFICATION)

    expectThat(stateMaps).isEmpty()
  }

  @Test
  fun `getContextsWithStatus with no pending verifications`() {
    context.setup()

    val contexts = subject.getVerificationContextsWithStatus(deliveryConfig, environment, PENDING)
    expectThat(contexts).isEmpty()
  }

  @Test
  fun `getContextsWithStatus with one pending verification`() {
    context.setup()
    subject.updateState(context, verification, PENDING)

    val contexts = subject.getVerificationContextsWithStatus(deliveryConfig, environment, PENDING)

    expectThat(contexts).containsExactly(context)
  }

  @Test
  fun `countVerifications with two pending verifications`() {
    context.setup()

    subject.updateState(context, verification, PENDING)
    val otherVerification = DummyVerification("other")
    subject.updateState(context, otherVerification, PENDING)


    val contexts = subject.getVerificationContextsWithStatus(deliveryConfig, environment, PENDING)
    expectThat(contexts).hasSize(2)
    expectThat(contexts).contains(context)
  }

  @Test
  fun `a post-deploy action is not counted as a verification`() {
    context.setup()

    subject.updateState(context, postDeployAction, PENDING)
    val contexts = subject.getVerificationContextsWithStatus(deliveryConfig, environment, PENDING)

    expectThat(contexts).isEmpty()
  }


  @Test
  fun `different delivery config does not interfere with the verification count of each other`() {
    context.setup()

    val otherEnvironment = Environment(name = "test", verifyWith = listOf(verification))

    val otherConfig = DeliveryConfig(
      application = "acme",
      name = "acme",
      serviceAccount = "acme@example.com",
      artifacts = setOf(DockerArtifact(name = "acme", deliveryConfigName = "acme", reference = "acme", branch = "main")),
      environments = setOf(otherEnvironment)
    )

    val otherContext = ArtifactInEnvironmentContext(
      deliveryConfig = otherConfig,
      environmentName = "test",
      artifactReference = "acme",
      version = "acme-123"
    )

    otherContext.setup()
    subject.updateState(otherContext, verification, PENDING)

    expectThat(subject.getVerificationContextsWithStatus(deliveryConfig, environment, PENDING)).isEmpty()
    expectThat(subject.getVerificationContextsWithStatus(otherConfig, otherEnvironment, PENDING)).containsExactly(otherContext)
  }
}
