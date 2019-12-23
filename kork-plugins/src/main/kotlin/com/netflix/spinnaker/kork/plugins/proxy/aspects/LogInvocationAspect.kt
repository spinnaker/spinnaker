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
import org.slf4j.LoggerFactory
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method

/**
 * Logs the invoked extension and method name.
 *
 * There is room for improvement here: The logger name could be mapped to the extension class and
 * there could be additional details logged (like arguments) based on configuration.
 */
class LogInvocationAspect : InvocationAspect<LogInvocationState> {
  private val log by lazy { LoggerFactory.getLogger(javaClass) }

  override fun supports(invocationState: Class<InvocationState>): Boolean {
    return invocationState == LogInvocationState::class.java
  }

  override fun before(
    target: Any,
    proxy: Any,
    method: Method,
    args: Array<out Any>?,
    descriptor: SpinnakerPluginDescriptor
  ): LogInvocationState {
    val logInvocationState = LogInvocationState(
      extensionName = target.javaClass.simpleName.toString(),
      methodName = method.name
    )

    log.trace("Invoking method={} on extension={}", logInvocationState.methodName,
      logInvocationState.extensionName)

    return logInvocationState
  }

  override fun after(invocationState: LogInvocationState) {
    log.trace("Successful execution of method={} on extension={}", invocationState.extensionName,
      invocationState.methodName)
  }

  override fun error(e: InvocationTargetException, invocationState: LogInvocationState) {
    log.error("Error invoking method={} on extension={}", invocationState.methodName,
      invocationState.extensionName, e.cause)
  }
}
