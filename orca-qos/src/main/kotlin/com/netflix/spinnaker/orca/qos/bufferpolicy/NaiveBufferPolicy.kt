/*
 * Copyright 2018 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.netflix.spinnaker.orca.qos.bufferpolicy

import com.netflix.spinnaker.orca.pipeline.model.Execution
import com.netflix.spinnaker.orca.qos.BufferAction
import com.netflix.spinnaker.orca.qos.BufferPolicy
import com.netflix.spinnaker.orca.qos.BufferResult
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component

@Component
@ConditionalOnProperty("qos.bufferPolicy.naive.enabled")
class NaiveBufferPolicy : BufferPolicy {

  override fun apply(execution: Execution): BufferResult = BufferResult(
    action = BufferAction.BUFFER,
    force = false,
    // If we're getting to the point of passing an execution through a BufferPolicy, the BufferStateSupplier has
    // already made buffering active. There's nothing to decide here.
    reason = "Naive policy will always buffer executions"
  )
}
