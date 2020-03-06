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

package com.netflix.spinnaker.kork.plugins.proxy.aspects

import com.netflix.spinnaker.kork.common.Header
import com.netflix.spinnaker.kork.plugins.SpinnakerPluginDescriptor
import dev.minutest.junit.JUnit5Minutests
import dev.minutest.rootContext
import io.mockk.mockk
import org.slf4j.MDC
import strikt.api.expectThat
import strikt.assertions.isA
import strikt.assertions.isEqualTo
import strikt.assertions.isFalse
import strikt.assertions.isNull
import strikt.assertions.isTrue
import java.lang.reflect.Method

class LogInvocationAspectTest : JUnit5Minutests {
  fun tests() = rootContext<Fixture> {
    fixture { Fixture() }

    test("creates LogInvocationState object with extension IDs") {
      val state = subject.before(target, proxy, method, args, spinnakerPluginDescriptor)
      expectThat(state).isA<LogInvocationState>()
        .and {
          get { extensionName }.isEqualTo(target.javaClass.simpleName.toString())
          get { methodName }.isEqualTo(method.name)
        }
    }

    test("LogInvocationAspect supports LogInvocationState") {
      val state = subject.before(target, proxy, method, args, spinnakerPluginDescriptor)
      val metricInvocationState = MetricInvocationState("Extension", 123, mockk(), mockk())
      expectThat(subject.supports(state.javaClass)).isTrue()
      expectThat(subject.supports(metricInvocationState.javaClass)).isFalse()
    }

    test("Plugin ID and extension name added to the MDC before invocation, removes in finally") {
      val invocatonState = subject.before(target, proxy, method, args, spinnakerPluginDescriptor)
      expectThat(MDC.get(Header.PLUGIN_ID.header)).isEqualTo(spinnakerPluginDescriptor.pluginId)
      expectThat(MDC.get(Header.PLUGIN_EXTENSION.header)).isEqualTo(target.javaClass.simpleName.toString())

      subject.finally(invocatonState)

      expectThat(MDC.get(Header.PLUGIN_ID.header)).isNull()
      expectThat(MDC.get(Header.PLUGIN_EXTENSION.header)).isNull()
    }
  }

  private inner class Fixture {
    val pluginId: String = "netflix.plugin"
    val pluginVersion: String = "0.0.1"
    val subject = LogInvocationAspect()

    val target: Any = SomeExtension()
    val proxy: Any = mockk(relaxed = true)
    val method: Method = createMethod()
    val args: Array<out Any> = arrayOf()
    val spinnakerPluginDescriptor: SpinnakerPluginDescriptor = createPluginDescriptor(pluginId, pluginVersion)
  }
}
