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
 *
 */

package com.netflix.spinnaker.kork.plugins.proxy

import com.netflix.spinnaker.kork.plugins.SpinnakerPluginDescriptor
import com.netflix.spinnaker.kork.plugins.api.internal.ExtensionInvocationHandler
import com.netflix.spinnaker.kork.plugins.api.internal.SpinnakerExtensionPoint
import com.netflix.spinnaker.kork.plugins.proxy.aspects.InvocationAspect
import com.netflix.spinnaker.kork.plugins.proxy.aspects.InvocationState
import java.lang.reflect.Method
import java.lang.reflect.Proxy

/**
 * A wrapper around [ExtensionInvocationProxy].
 * The provided `target` is resolved at method-call time.
 *
 * Used by [com.netflix.spinnaker.kork.plugins.v2.SpinnakerPluginService] to define
 * beans that are injected early in Spring's lifecycle but whose implementations
 * are resolved late - only when the extension's methods are called for the first time.
 * */
class LazyExtensionInvocationProxy(
  private val target: Lazy<SpinnakerExtensionPoint>,
  private val targetClass: Class<out SpinnakerExtensionPoint>,
  private val invocationAspects: List<InvocationAspect<InvocationState>>,
  private val descriptor: SpinnakerPluginDescriptor
) : ExtensionInvocationHandler {

  private val delegate by lazy { ExtensionInvocationProxy(target.value, invocationAspects, descriptor) }

  override fun getTargetClass(): Class<out SpinnakerExtensionPoint> = targetClass

  override fun invoke(proxy: Any, method: Method, args: Array<out Any>?) = delegate.invoke(proxy, method, args)

  companion object {
    /**
     * Factory method for wrapping a [SpinnakerExtensionPoint] in a [LazyExtensionInvocationProxy].
     */
    fun proxy(
      target: Lazy<SpinnakerExtensionPoint>,
      targetClass: Class<out SpinnakerExtensionPoint>,
      invocationAspects: List<InvocationAspect<InvocationState>>,
      descriptor: SpinnakerPluginDescriptor
    ): Any = Proxy.newProxyInstance(
      targetClass.classLoader,
      targetClass.interfaces,
      LazyExtensionInvocationProxy(target, targetClass, invocationAspects, descriptor)
    )
  }
}
