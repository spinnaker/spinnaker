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
import com.netflix.spinnaker.kork.plugins.api.internal.SpinnakerExtensionPoint
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method

/**
 * An aspect to use with [ExtensionInvocationProxy], allows for storing state about an invocation
 * and accessing that state on [error] and [after] the invocation.
 */
interface InvocationAspect<I : InvocationState> {

  /**
   * Determines if the instance supports the specified [InvocationState] type.
   *
   * @param invocationState The [InvocationState] type that this aspect supports
   */
  fun supports(invocationState: Class<InvocationState>): Boolean

  /**
   * Called prior to method invocation.
   *
   * The params [proxy], [method], and [args] are documented at [java.lang.reflect.InvocationHandler]
   *
   * @param target The target object that is being proxied
   * @param descriptor The [SpinnakerPluginDescriptor] provides metadata about the plugin
   *
   * @return I The [InvocationState] instance, which is used to store state about the invocation.
   */
  fun before(target: SpinnakerExtensionPoint, proxy: Any, method: Method, args: Array<out Any>?, descriptor: SpinnakerPluginDescriptor): I

  /**
   * After method invocation. Called immediately after invoking the method.
   *
   * @param invocationState The state object created via [before]
   */
  fun after(invocationState: I)

  /**
   * If the method invocation threw an InvocationTargetException, apply some additional processing if
   * desired.  Called in a catch block.
   *
   * @param e InvocationTargetException which is thrown via
   * @param invocationState The invocationState object created via [before]
   */
  fun error(e: InvocationTargetException, invocationState: I)

  /**
   * Called last and always called, regardless of invocation success or failure.
   *
   * Optional, default implementation is a no-op.
   *
   * @param invocationState The invocationState object created via [before]
   */
  fun finally(invocationState: I) { /* default implementation */ }
}
