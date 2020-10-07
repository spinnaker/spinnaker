/*
 * Copyright 2020 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.netflix.spinnaker.kork.plugins.v2

import com.netflix.spinnaker.kork.plugins.api.internal.SpinnakerExtensionPoint
import com.netflix.spinnaker.kork.plugins.events.ExtensionCreated
import dev.minutest.junit.JUnit5Minutests
import dev.minutest.rootContext
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.pf4j.PluginWrapper
import org.springframework.beans.factory.support.GenericBeanDefinition
import org.springframework.context.ApplicationEventPublisher
import org.springframework.context.support.GenericApplicationContext
import strikt.api.expectThat
import strikt.api.expectThrows
import strikt.assertions.isA
import strikt.assertions.isEqualTo
import strikt.assertions.isNotNull
import java.lang.IllegalArgumentException

class ExtensionPromotionBeanPostProcessorTest : JUnit5Minutests {

  fun tests() = rootContext<Fixture> {
    fixture { Fixture() }

    test("requires bean names to be non-null") {
      every { pluginApplicationContext.getBeanDefinition("myBean") } returns GenericBeanDefinition()

      expectThrows<IllegalArgumentException> {
        subject.postProcessAfterInitialization(MyExtensionBean(), "myBean")
      }
    }

    listOf(
      MyExtensionBean(),
      MyOtherBean()
    ).forEach { bean ->
      val shouldPromote = bean is SpinnakerExtensionPoint
      val testPrefix = if (shouldPromote) "should" else "should not"

      test("$testPrefix promote ${bean.javaClass.simpleName} to service application context") {
        val beanName = bean.javaClass.simpleName.toLowerCase()

        every { pluginWrapper.pluginClassLoader } returns ClassLoader.getSystemClassLoader()
        every { pluginWrapper.pluginId } returns "plugin.id"
        every { pluginApplicationContext.getBeanDefinition(beanName) } returns GenericBeanDefinition()
          .apply {
            beanClassName = bean.javaClass.name
          }

        subject.postProcessAfterInitialization(bean, beanName)

        verify(exactly = if (shouldPromote) 1 else 0) {
          beanPromoter.promote(eq("plugin.id_$beanName"), eq(bean), eq(bean.javaClass))
          applicationEventPublisher.publishEvent(withArg {
            expectThat(it).isA<ExtensionCreated>().and {
              get { source }.isNotNull()
              get { beanName }.isEqualTo(beanName)
              get { bean }.isEqualTo(bean)
              get { beanClass }.isEqualTo(bean.javaClass)
            }
          })
        }
      }
    }
  }

  private inner class MyExtensionBean : SpinnakerExtensionPoint
  private inner class MyOtherBean

  private inner class Fixture {
    val pluginWrapper: PluginWrapper = mockk(relaxed = true)
    val pluginApplicationContext: GenericApplicationContext = mockk(relaxed = true)
    val beanPromoter: BeanPromoter = mockk(relaxed = true)
    val applicationEventPublisher: ApplicationEventPublisher = mockk(relaxed = true)

    val subject = ExtensionPromotionBeanPostProcessor(
      pluginWrapper,
      pluginApplicationContext,
      beanPromoter,
      applicationEventPublisher
    )
  }
}
