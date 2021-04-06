package com.netflix.spinnaker.keel.enforcers

import com.netflix.spinnaker.keel.api.DeliveryConfig
import com.netflix.spinnaker.keel.api.Environment
import com.netflix.spinnaker.keel.api.Verification
import com.netflix.spinnaker.keel.api.constraints.ConstraintStatus
import com.netflix.spinnaker.keel.api.verification.VerificationContext
import com.netflix.spinnaker.keel.api.verification.VerificationRepository
import com.netflix.spinnaker.keel.artifacts.DockerArtifact
import com.netflix.spinnaker.keel.exceptions.ActiveLeaseExists
import com.netflix.spinnaker.keel.persistence.ArtifactRepository
import com.netflix.spinnaker.keel.persistence.EnvironmentLeaseRepository
import io.mockk.Called
import io.mockk.coVerify as verify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test

import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import strikt.api.expectCatching
import strikt.assertions.isA
import strikt.assertions.isFailure

import org.springframework.core.env.Environment as SpringEnvironment

internal class EnvironmentExclusionEnforcerTest {

  private val springEnv : SpringEnvironment = mockk {
    every { getProperty("keel.enforcement.environment-exclusion.enabled", Boolean::class.java, any())} returns true
  }

  private val verificationRepository = mockk<VerificationRepository>() {
    every { getContextsWithStatus(any(), any(), any()) }  returns emptyList()
  }
  private val artifactRepository = mockk<ArtifactRepository>()
  private val environmentLeaseRepository = mockk<EnvironmentLeaseRepository>(relaxUnitFun = true) {
    every { tryAcquireLease(any(), any(), any()) } returns mockk(relaxUnitFun = true)
  }

  data class DummyVerification(val value: String) : Verification {
    override val type = "dummy"
    override val id: String = "$type:$value"
  }

  private val enforcer = EnvironmentExclusionEnforcer(springEnv, verificationRepository, artifactRepository, environmentLeaseRepository)
  private val verification = DummyVerification("1")
  private val environment = Environment(name="test", verifyWith=listOf(verification))
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
    ),
    environments = setOf( environment )
  )


  @Test
  fun `withVerificationLease invokes the action when there are no pending verifications or deployments`() {
    every { artifactRepository.isDeployingTo(any(), any()) } returns false
    every { verificationRepository.getContextsWithStatus(any(), any(), ConstraintStatus.PENDING) } returns emptyList()
    val action : () -> Unit = mockk(relaxed=true)

    enforcer.withVerificationLease(mockk(relaxed=true), action)

    verify(exactly=1) {
      action.invoke()
    }
  }

  @Test
  fun `withActuationLease invokes the action when there are no pending verifications`() {
    every { verificationRepository.getContextsWithStatus(any(), any(), ConstraintStatus.PENDING) } returns emptyList()
    val action : suspend () -> Unit = mockk(relaxed=true)
    runBlocking {
      enforcer.withActuationLease(mockk(), mockk(), action)
    }

    verify(exactly=1) {
      action.invoke()
    }
  }


  @Test
  fun `withActuationLease does not invoke the action when there are pending verifications`() {
    every { verificationRepository.getContextsWithStatus(any(), any(), ConstraintStatus.PENDING) } returns
      listOf(VerificationContext(deliveryConfig, "test", "fnord-docker-stable", "fnord-0.190.0-h378.eacb135"))

    val action : suspend () -> Unit = mockk(relaxed=true)

    expectCatching {
      runBlocking {
        enforcer.withActuationLease(deliveryConfig, environment, action)
      }
    }.isFailure()
      .isA<ActiveVerifications>()

    verify {
      action wasNot Called
    }
  }

  @Test
  fun `withVerificationLease throws an exception when there are pending verifications`() {
    every { artifactRepository.isDeployingTo(any(), any()) } returns false
    every { verificationRepository.getContextsWithStatus(any(), any(), ConstraintStatus.PENDING) } returns listOf(mockk(relaxed=true))

    val action : () -> Unit = mockk(relaxed=true)

    expectCatching { enforcer.withVerificationLease(mockk(relaxed=true), action) }
      .isFailure()
      .isA<ActiveVerifications>()

    verify {
      action wasNot Called
    }
  }

  @Test
  fun `withVerificationLease throws an exception when there are ongoing deployments`() {
    every { verificationRepository.getContextsWithStatus(any(), any(), ConstraintStatus.PENDING) } returns emptyList()
    every { artifactRepository.isDeployingTo(any(), any()) } returns true

    val action : () -> Unit = mockk(relaxed=true)

    expectCatching { enforcer.withVerificationLease(mockk(relaxed=true), action) }
      .isFailure()
      .isA<ActiveDeployments>()

    verify {
      action wasNot Called
    }
  }

  @Test
  fun `withVerificationLease  throw an exception when there is an active lease`() {
    val environment = mockk<Environment>() {
      every { name } returns "testing"
    }
    every { environmentLeaseRepository.tryAcquireLease(any(), any(), any()) } throws ActiveLeaseExists(environment, "mystery", mockk())
    val action : () -> Unit = mockk(relaxed=true)
    expectCatching { enforcer.withVerificationLease(mockk(relaxed=true), action) }
      .isFailure()
      .isA<ActiveLeaseExists>()
  }


  @ParameterizedTest(name="withVerificationLease executes action, enabled={0}")
  @ValueSource(booleans = [true, false])
  fun `withVerificationLease invokes the action whether the feature flag is enabled or not`(enabled: Boolean) {
    every { springEnv.getProperty("keel.enforcement.environment-exclusion.enabled", Boolean::class.java, any())} returns enabled

    every { verificationRepository.getContextsWithStatus(any(), any(), ConstraintStatus.PENDING) } returns emptyList()
    every { artifactRepository.isDeployingTo(any(), any()) } returns false

    val action : () -> Unit = mockk(relaxed=true)

    enforcer.withVerificationLease(mockk(relaxed=true), action)

    verify(exactly=1) {
      action.invoke()
    }
  }

  @ParameterizedTest(name="withActuationLease executes action, enabled={0}")
  @ValueSource(booleans = [true, false])
  fun `withActuationLease invokes the action whether the feature flag is enabled or not`(enabled: Boolean) {
    every { springEnv.getProperty("keel.enforcement.environment-exclusion.enabled", Boolean::class.java, any())} returns enabled

    every { verificationRepository.getContextsWithStatus(any(), any(), ConstraintStatus.PENDING) } returns emptyList()
    every { artifactRepository.isDeployingTo(any(), any()) } returns false

    val action : suspend () -> Unit = mockk(relaxed=true)
    runBlocking {
      enforcer.withActuationLease(mockk(), mockk(), action)
    }

    verify(exactly=1) {
      action.invoke()
    }
  }
}
