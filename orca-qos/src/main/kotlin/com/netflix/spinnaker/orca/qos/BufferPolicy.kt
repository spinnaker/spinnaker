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
package com.netflix.spinnaker.orca.qos

import com.netflix.spinnaker.orca.pipeline.model.Execution
import org.springframework.core.Ordered

/**
 * A policy responsible for determining whether an [Execution] should be buffered.
 */
interface BufferPolicy : Ordered {

  fun apply(execution: Execution): BufferResult

  override fun getOrder() = 0
}

enum class BufferAction {
  /**
   * Marks the execution for buffering.
   */
  BUFFER,

  /**
   * Marks the execution for moving from buffered to queued.
   */
  ENQUEUE
}

/**
 * @param action The action to take
 * @param force If true, the chain of policies will be short-circuited and this result's action will be used.
 * @param reason A human-friendly reason for the action.
 */
data class BufferResult(
  val action: BufferAction,
  val force: Boolean,
  val reason: String
)
