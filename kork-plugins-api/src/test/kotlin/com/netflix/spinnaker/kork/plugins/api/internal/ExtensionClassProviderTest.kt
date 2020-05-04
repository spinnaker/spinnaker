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
 *
 */

package com.netflix.spinnaker.kork.plugins.api.internal

import dev.minutest.junit.JUnit5Minutests
import dev.minutest.rootContext
import strikt.api.expectThat
import java.lang.reflect.Method
import java.lang.reflect.Proxy

class ExtensionInvocationHandlerProviderTest : JUnit5Minutests {

  fun tests() = rootContext<Fixture> {
    fixture { Fixture() }

    test("Provides the proxied class") {
      val extensionClass = proxy.extensionClass
      expectThat(extensionClass == extension.javaClass)
    }

    test("Passes through the class if not proxied") {
      val extensionClass = extension.extensionClass
      expectThat(extensionClass == extension.javaClass)
    }
  }

  private inner class Fixture {
    val extension = SomeExtension()
    val proxy = ProxyExtension.proxy(extension) as SpinnakerExtensionPoint
  }
}

class SomeExtension : SpinnakerExtensionPoint

class ProxyExtension(private val target: SpinnakerExtensionPoint): ExtensionInvocationHandler {
  override fun getTargetClass(): Class<out SpinnakerExtensionPoint> {
    return target.javaClass
  }

  override fun invoke(proxy: Any, method: Method, args: Array<out Any>?): Any {
    return method.invoke(target, *(args ?: arrayOfNulls<Any>(0)))
  }

  companion object {
    fun proxy(
      target: SpinnakerExtensionPoint
    ): Any {
      return Proxy.newProxyInstance(
        target.javaClass.classLoader,
        target.javaClass.interfaces,
        ProxyExtension(target)
      )
    }
  }
}
