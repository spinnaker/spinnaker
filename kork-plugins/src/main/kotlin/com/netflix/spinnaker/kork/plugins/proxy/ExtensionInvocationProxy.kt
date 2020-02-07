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

package com.netflix.spinnaker.kork.plugins.proxy

import com.netflix.spinnaker.kork.plugins.SpinnakerPluginDescriptor
import com.netflix.spinnaker.kork.plugins.proxy.aspects.InvocationState
import com.netflix.spinnaker.kork.plugins.proxy.aspects.InvocationAspect
import java.lang.RuntimeException
import java.lang.reflect.InvocationHandler
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method
import java.lang.reflect.Proxy

/**
 * The invocation proxy for extensions.  Supports a list of [InvocationAspect] objects which
 * provides a pattern for instrumenting method invocation.
 */
class ExtensionInvocationProxy(
  private val target: Any,
  private val invocationAspects: List<InvocationAspect<InvocationState>>,
  private val pluginDescriptor: SpinnakerPluginDescriptor
) : InvocationHandler {

  /**
   * Target class is exposed here so we can determine extension type via [ExtensionClassProvider]
   */
  internal fun getTargetClass(): Class<*> {
    return target.javaClass
  }

  override fun invoke(proxy: Any, method: Method, args: Array<out Any>?): Any {
    val invocationStates: MutableSet<InvocationState> = mutableSetOf()
    invocationStates.before(proxy, method, args)

    val result: Any
    try {
      result = method.invoke(target, *(args ?: arrayOfNulls<Any>(0)))
      invocationStates.after()
    } catch (e: InvocationTargetException) {
      invocationStates.error(e)
      throw e.cause ?: RuntimeException("Caught invocation target exception without cause.", e)
    }

    return result
  }

  private fun MutableSet<InvocationState>.before(proxy: Any, method: Method, args: Array<out Any>?) {
    invocationAspects.forEach {
      this.add(it.before(target, proxy, method, args, pluginDescriptor))
    }
  }

  private fun MutableSet<InvocationState>.error(e: InvocationTargetException) {
    this.forEach { invocationState ->
      invocationAspects.forEach { invocationAspect ->
        if (invocationAspect.supports((invocationState.javaClass))) {
          invocationAspect.error(e, invocationState)
        }
      }
    }
  }

  private fun MutableSet<InvocationState>.after() {
    this.forEach { invocationState ->
      invocationAspects.forEach { invocationAspect ->
        if (invocationAspect.supports((invocationState.javaClass))) {
          invocationAspect.after(invocationState)
        }
      }
    }
  }

  companion object {
    fun proxy(
      target: Any,
      invocationAspects: List<InvocationAspect<InvocationState>>,
      descriptor: SpinnakerPluginDescriptor
    ): Any {
      return Proxy.newProxyInstance(
        target.javaClass.classLoader,
        target.javaClass.interfaces,
        ExtensionInvocationProxy(target, invocationAspects, descriptor)
      )
    }
  }
}
