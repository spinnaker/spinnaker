/*
 * Copyright 2019 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.netflix.spinnaker.clouddriver.saga

import com.netflix.spectator.api.NoopRegistry
import com.netflix.spinnaker.clouddriver.saga.models.Saga
import com.netflix.spinnaker.clouddriver.saga.persistence.SagaRepository
import dev.minutest.junit.JUnit5Minutests
import io.mockk.every
import io.mockk.mockk
import org.springframework.context.ApplicationContext

abstract class AbstractSagaTest : JUnit5Minutests {

  protected open inner class BaseSagaFixture(options: FixtureOptions = FixtureOptions()) {
    val saga = Saga("test", "test")

    val sagaRepository: SagaRepository

    val applicationContext: ApplicationContext = mockk(relaxed = true)

    val sagaService: SagaService

    init {
      if (options.mockSaga) {
        sagaRepository = mockk(relaxed = true)
        every { sagaRepository.get(eq("test"), eq("test")) } returns saga
      } else {
        sagaRepository = TestingSagaRepository()
      }
      if (options.registerDefaultTestTypes) {
        registerBeans(
          applicationContext,
          Action1::class.java,
          Action2::class.java,
          Action3::class.java,
          ShouldBranchPredicate::class.java
        )
      }

      sagaService = SagaService(sagaRepository, NoopRegistry()).apply {
        setApplicationContext(applicationContext)
      }
    }
  }

  /**
   * @param mockSaga Whether or not to use mockk for the [SagaRepository] or the [TestingSagaRepository]
   * @param registerDefaultTestTypes Whether or not to register the canned test types for "autowiring"
   */
  open inner class FixtureOptions(
    val mockSaga: Boolean = false,
    val registerDefaultTestTypes: Boolean = true
  )

  protected fun registerBeans(applicationContext: ApplicationContext, vararg clazz: Class<*>) {
    clazz.forEach {
      every { applicationContext.getBean(eq(it)) } returns it.newInstance()
    }
  }
}
