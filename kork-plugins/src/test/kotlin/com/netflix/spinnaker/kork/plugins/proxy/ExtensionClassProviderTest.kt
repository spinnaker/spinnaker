/*
 * Copyright 2020 Netflix, Inc.
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

package com.netflix.spinnaker.kork.plugins.proxy

import com.netflix.spinnaker.kork.plugins.SpinnakerPluginDescriptor
import com.netflix.spinnaker.kork.plugins.proxy.aspects.InvocationAspect
import com.netflix.spinnaker.kork.plugins.proxy.aspects.InvocationState
import com.netflix.spinnaker.kork.plugins.proxy.aspects.SomeExtension
import com.netflix.spinnaker.kork.plugins.proxy.aspects.createPluginDescriptor
import dev.minutest.junit.JUnit5Minutests
import dev.minutest.rootContext
import io.mockk.mockk
import org.pf4j.ExtensionPoint
import strikt.api.expectThat

class ExtensionClassProviderTest : JUnit5Minutests {

  fun tests() = rootContext<Fixture> {
    fixture { Fixture() }

    test("Provides the proxied class") {
      val proxiedClass = ExtensionClassProvider.getExtensionClass(proxiedExtension)
      expectThat(proxiedClass == extension.javaClass)
    }
  }

  private inner class Fixture {
    val invocationAspects: List<InvocationAspect<InvocationState>> = mockk(relaxed = true)
    val spinnakerPluginDescriptor: SpinnakerPluginDescriptor = createPluginDescriptor("netflix.plugin", "0.0.1")
    val extension = SomeExtension()
    val proxiedExtension = ExtensionInvocationProxy.proxy(extension, invocationAspects, spinnakerPluginDescriptor) as ExtensionPoint
  }
}
