/*
 * Copyright 2017 Netflix, Inc.
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
package com.netflix.spinnaker.orca.q

import java.io.Serializable
import java.time.Instant

data class ExecutionLogEntry(
  val executionId: String,
  val timestamp: Instant,
  val eventType: String,
  val details: Map<String, Serializable>,
  var currentInstanceId: String
)

interface ExecutionLogRepository {
  fun save(entry: ExecutionLogEntry)
}

/**
 * Fallback (yet extra-unsafe) execution log repository.
 */
class BlackholeExecutionLogRepository : ExecutionLogRepository {
  override fun save(entry: ExecutionLogEntry) {}
}
