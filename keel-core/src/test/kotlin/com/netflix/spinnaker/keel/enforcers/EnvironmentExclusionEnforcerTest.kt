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

  private val springEnv : SpringEnvironment = mockk() {
    every { getProperty("keel.enforcement.environment-exclusion.enabled", Boolean::class.java, any())} returns true
  }

  private val verificationRepository = mockk<VerificationRepository>() {
    every { countVerifications(any(), any(), any()) }  returns 0
  }

  private val enforcer = EnvironmentExclusionEnforcer(springEnv, verificationRepository, NoopRegistry(), Clock.systemUTC())

  @Test
  fun `invoke the action when there are no pending verifications`() {
    every { verificationRepository.countVerifications(any(), any(), ConstraintStatus.PENDING) } returns 0
    val action : () -> Unit = mockk(relaxed=true)

    enforcer.withVerificationLease(mockk(relaxed=true), action)

    verify(exactly=1) {
      action.invoke()
    }
  }

  @Test
  fun `throw an exception when there are pending verifications`() {
    every { verificationRepository.countVerifications(any(), any(), ConstraintStatus.PENDING) } returns 1

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
