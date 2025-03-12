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

import com.netflix.spectator.api.Id

/**
 * Interface representing invocation state, used with [InvocationAspect] to process method
 * invocation.  All subclasses of [InvocationState] are expected to be immutable - properties must
 * be declared with `val`.
 */
interface InvocationState

/**
 * Tracks the state of a method invocation for the purposes of metrics collection.
 *
 * @param extensionName The name of the extension
 * @param startTimeMs The time the method invocation started
 * @param timingId The optional metric ID, if left unset, one will be assigned automatically based on the method name
 */
data class MetricInvocationState(
  internal val extensionName: String,
  internal val startTimeMs: Long,
  internal val timingId: Id?
) : InvocationState

/**
 * Tracks the state of a method invocation for the purposes of logging.
 *
 * @param extensionName The name of the extension
 * @param methodName The method name that is calling the log
 */
data class LogInvocationState(
  internal val extensionName: String,
  internal val methodName: String
) : InvocationState
