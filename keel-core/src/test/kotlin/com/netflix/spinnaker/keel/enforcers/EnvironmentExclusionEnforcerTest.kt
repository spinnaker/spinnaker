package com.netflix.spinnaker.keel.enforcers

import com.netflix.spectator.api.NoopRegistry
import com.netflix.spinnaker.keel.api.constraints.ConstraintStatus
import com.netflix.spinnaker.keel.api.verification.VerificationRepository
import io.mockk.Called
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test

import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import strikt.api.expectCatching
import strikt.assertions.isA
import strikt.assertions.isFailure
import java.time.Clock

import org.springframework.core.env.Environment as SpringEnvironment

internal class EnvironmentExclusionEnforcerTest {

  private val springEnv : SpringEnvironment = mockk {
    every { getProperty("keel.enforcement.environment-exclusion.enabled", Boolean::class.java, any())} returns true
  }

  private val verificationRepository = mockk<VerificationRepository>() {
    every { getContextsWithStatus(any(), any(), any()) }  returns emptyList()
  }

  private val enforcer = EnvironmentExclusionEnforcer(springEnv, verificationRepository, NoopRegistry(), Clock.systemUTC())

  @Test
  fun `invoke the action when there are no pending verifications`() {
    every { verificationRepository.getContextsWithStatus(any(), any(), ConstraintStatus.PENDING) } returns emptyList()
    val action : () -> Unit = mockk(relaxed=true)

    enforcer.withVerificationLease(mockk(relaxed=true), action)

    verify(exactly=1) {
      action.invoke()
    }
  }

  @Test
  fun `throw an exception when there are pending verifications`() {
    every { verificationRepository.getContextsWithStatus(any(), any(), ConstraintStatus.PENDING) } returns listOf(mockk(relaxed=true))

    val action : () -> Unit = mockk(relaxed=true)

    expectCatching { enforcer.withVerificationLease(mockk(relaxed=true), action) }
      .isFailure()
      .isA<ActiveVerifications>()

    verify {
      action wasNot Called
    }
  }


  @ParameterizedTest(name="withVerificationLease executes action, enabled={0}")
  @ValueSource(booleans = [true, false])
  fun `withVerificationLease invokes the action whether the feature flag is enabled or not`(enabled: Boolean) {
    every { springEnv.getProperty("keel.enforcement.environment-exclusion.enabled", Boolean::class.java, any())} returns enabled

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

    val action : suspend () -> Unit = mockk(relaxed=true)
    runBlocking {
      enforcer.withActuationLease(mockk(), mockk(), action)
    }

    coVerify(exactly=1) {
      action.invoke()
    }
  }
}
