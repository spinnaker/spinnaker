/*
 * Copyright 2018 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
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

package com.netflix.spinnaker.orca.q.pending

import com.netflix.spinnaker.orca.pipeline.model.Execution
import com.netflix.spinnaker.q.Message

/**
 * Used to prevent multiple executions of the same pipeline from running
 * concurrently if [Execution.limitConcurrent] is `true`.
 */
interface PendingExecutionService {
  fun enqueue(pipelineConfigId: String, message: Message)
  fun popOldest(pipelineConfigId: String): Message?
  fun popNewest(pipelineConfigId: String): Message?
  fun purge(pipelineConfigId: String, callback: (Message) -> Unit)
  fun depth(pipelineConfigId: String): Int
}

