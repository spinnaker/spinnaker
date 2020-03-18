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

import com.netflix.spinnaker.kork.plugins.SpinnakerPluginDescriptor
import io.mockk.every
import io.mockk.mockk
import java.lang.reflect.Method
import org.pf4j.ExtensionPoint

internal fun createPluginDescriptor(pluginId: String, version: String): SpinnakerPluginDescriptor {
  val descriptor: SpinnakerPluginDescriptor = mockk(relaxed = true)
  every { descriptor.pluginId } returns pluginId
  every { descriptor.version } returns version
  return descriptor
}

internal fun createMethod(): Method {
  return SomeExtension::class.java.getMethod("helloWorld")
}

internal fun createPrivateMethod(): Method {
  return SomeExtension::class.java.getDeclaredMethod("privateHelloWorld")
}

internal class SomeExtension : ExtensionPoint {

  /**
   * Public helloWorld method, exists to test public method instrumentation.
   */
  fun helloWorld(): String {
    return "Hello Public World!"
  }

  /**
   * Private helloWorld method, exists to test private method instrumentation (or lack thereof).
   */
  private fun privateHelloWorld(): String {
    return "Hello Private World!"
  }
}
