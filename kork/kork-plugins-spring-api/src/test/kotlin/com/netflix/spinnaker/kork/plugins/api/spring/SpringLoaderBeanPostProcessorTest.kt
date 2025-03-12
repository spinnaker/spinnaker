/*
 * Copyright 2020 Armory, Inc.
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

package com.netflix.spinnaker.kork.plugins.api.spring

import dev.minutest.junit.JUnit5Minutests
import dev.minutest.rootContext
import io.mockk.mockk
import io.mockk.verify
import org.springframework.context.annotation.AnnotationConfigApplicationContext
import org.springframework.context.annotation.Primary
import org.springframework.web.bind.annotation.RestController

class SpringLoaderBeanPostProcessorTest : JUnit5Minutests {

  fun tests() = rootContext<Fixture> {
    fixture {
      Fixture()
    }

    test("regular class should not be exposed") {
      val bean = MyService()
      beanPostProcessor.postProcessAfterInitialization(bean, "springLoaderBeanPostProcessorTest.MyService")
      verify(exactly = 0) {
        beanPromoter.promote(any(), any(), any(), any())
      }
    }

    test("exposed non primary class should be exposed") {
      val bean = MyExposedService()
      val beanName = "springLoaderBeanPostProcessorTest.MyExposedService"
      beanPostProcessor.postProcessAfterInitialization(bean, beanName)
      verify(exactly = 1) {
        beanPromoter.promote(beanName, bean, MyExposedService::class.java, false)
      }
    }

    test("primary class should not be exposed") {
      val bean = MyPrimaryService()
      beanPostProcessor.postProcessAfterInitialization(bean, "springLoaderBeanPostProcessorTest.MyPrimaryService")
      verify(exactly = 0) {
        beanPromoter.promote(any(), any(), any(), any())
      }
    }

    test("exposed primary class should be exposed") {
      val bean = MyExposedPrimaryService()
      val beanName = "springLoaderBeanPostProcessorTest.MyExposedPrimaryService"
      beanPostProcessor.postProcessAfterInitialization(bean, beanName)
      verify(exactly = 1) {
        beanPromoter.promote(beanName, bean, MyExposedPrimaryService::class.java, true)
      }
    }

    test("controller should be exposed") {
      val bean = MyController()
      val beanName = "springLoaderBeanPostProcessorTest.MyController"
      beanPostProcessor.postProcessAfterInitialization(bean, beanName)
      verify(exactly = 1) {
        beanPromoter.promote(beanName, bean, MyController::class.java, false)
      }
    }

  }

  private inner class Fixture {
    val pluginContext = AnnotationConfigApplicationContext()
    val beanPromoter: BeanPromoter = mockk(relaxed = true)
    val beanPostProcessor = SpringLoaderBeanPostProcessor(pluginContext, beanPromoter)

    init {
      pluginContext.classLoader = javaClass.classLoader
      pluginContext.register(MyService::class.java)
      pluginContext.register(MyExposedService::class.java)
      pluginContext.register(MyPrimaryService::class.java)
      pluginContext.register(MyExposedPrimaryService::class.java)
      pluginContext.register(MyController::class.java)
    }
  }

  internal class MyService

  @ExposeToApp
  internal class MyExposedService

  @Primary
  internal class MyPrimaryService

  @ExposeToApp
  @Primary
  internal class MyExposedPrimaryService

  @RestController
  internal class MyController
}
