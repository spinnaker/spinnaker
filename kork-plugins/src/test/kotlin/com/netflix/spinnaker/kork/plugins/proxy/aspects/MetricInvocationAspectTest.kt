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

import com.netflix.spectator.api.BasicTag
import com.netflix.spectator.api.Clock
import com.netflix.spectator.api.Counter
import com.netflix.spectator.api.DefaultRegistry
import com.netflix.spectator.api.Functions
import com.netflix.spectator.api.Id
import com.netflix.spectator.api.Registry
import com.netflix.spectator.api.Timer
import com.netflix.spinnaker.kork.plugins.SpinnakerPluginDescriptor
import dev.minutest.junit.JUnit5Minutests
import dev.minutest.rootContext
import io.mockk.every
import io.mockk.mockk
import java.lang.reflect.Method
import java.util.stream.Collectors
import org.springframework.beans.factory.ObjectProvider
import strikt.api.expectThat
import strikt.assertions.isA
import strikt.assertions.isEqualTo
import strikt.assertions.isFalse
import strikt.assertions.isNull
import strikt.assertions.isTrue

class MetricInvocationAspectTest : JUnit5Minutests {

  fun tests() = rootContext<Fixture> {
    fixture { Fixture() }

    test("creates MetricInvocationState object with meter IDs") {
      val state = subject.before(target, proxy, method, args, spinnakerPluginDescriptor)

      expectThat(state).isA<MetricInvocationState>()
        .and {
          get { startTimeMs }.isA<Long>()
          get { timingId }.isA<Id>().and {
            get { name() }.isEqualTo("$pluginId.helloWorld.timing")
            get { tags().iterator().asSequence().toList() }.isEqualTo(
              listOf(BasicTag("pluginExtension", target.javaClass.simpleName.toString()),
                BasicTag("pluginVersion", pluginVersion)))
          }
          get { invocationsId }.isA<Id>().and {
            get { name() }.isEqualTo("$pluginId.helloWorld.invocations")
            get { tags().iterator().asSequence().toList() }.isEqualTo(
              listOf(BasicTag("pluginExtension", target.javaClass.simpleName.toString()),
                BasicTag("pluginVersion", pluginVersion)))
          }
          get { extensionName }.isA<String>().isEqualTo(target.javaClass.simpleName.toString())
        }
    }

    test("Private method is not instrumented with meters") {
      val state = subject.before(target, proxy, privateMethod, args, spinnakerPluginDescriptor)
      expectThat(state).isA<MetricInvocationState>()
        .and {
          get { timingId }.isNull()
          get { invocationsId }.isNull()
        }
    }

    test("Processes MetricInvocationState object after method invocations, meters are correct") {
      // One method invocation
      val state1 = subject.before(target, proxy, method, args, spinnakerPluginDescriptor)
      subject.after(state1)

      // Another method invocation
      val state2 = subject.before(target, proxy, method, args, spinnakerPluginDescriptor)
      subject.after(state2)

      val counterSummary = registry.counters().filter(Functions.nameEquals("$pluginId.helloWorld.invocations")).collect(Collectors.summarizingLong(Counter::count))
      val timerCountSummary = registry.timers().filter(Functions.nameEquals("$pluginId.helloWorld.timing")).collect(Collectors.summarizingLong(Timer::count))

      // There should be two metric points for each meter type
      expectThat(counterSummary).get { sum }.isEqualTo(2)
      expectThat(timerCountSummary).get { sum }.isEqualTo(2)
    }

    test("MetricInvocationAspect supports MetricInvocationState") {
      val state = subject.before(target, proxy, method, args, spinnakerPluginDescriptor)
      val logState = LogInvocationState("foo", "bar")
      expectThat(subject.supports(state.javaClass)).isTrue()
      expectThat(subject.supports(logState.javaClass)).isFalse()
    }
  }

  private inner class Fixture {
    val pluginId: String = "netflix.plugin"
    val pluginVersion: String = "0.0.1"

    val registry: Registry = DefaultRegistry(Clock.SYSTEM)
    val registryProvider: ObjectProvider<Registry> = mockk(relaxed = true)
    val subject = MetricInvocationAspect(registryProvider)

    val target: Any = SomeExtension()
    val proxy: Any = mockk(relaxed = true)
    val method: Method = createMethod()
    val privateMethod: Method = createPrivateMethod()
    val args: Array<out Any> = arrayOf()
    val spinnakerPluginDescriptor: SpinnakerPluginDescriptor = createPluginDescriptor(pluginId, pluginVersion)

    init {
      every { registryProvider.ifAvailable } returns registry
    }
  }
}
