package com.netflix.spinnaker.keel.enforcers

import io.mockk.coVerify
import kotlinx.coroutines.test.runBlockingTest
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify

import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

import org.springframework.core.env.Environment as SpringEnvironment

internal class EnvironmentExclusionEnforcerTest {

  private val springEnv : SpringEnvironment = mockk()
  private val enforcer = EnvironmentExclusionEnforcer(springEnv)

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
    runBlockingTest {
      enforcer.withActuationLease(mockk(), action)
    }

    coVerify(exactly=1) {
      action.invoke()
    }
  }
}
