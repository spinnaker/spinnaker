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
package com.netflix.spinnaker.config

import java.util.concurrent.TimeUnit
import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties("sql.agent.task-cleanup")
class SqlTaskCleanupAgentProperties {
  var completedTtlMs: Long = TimeUnit.DAYS.toMillis(4)
  /**
   * How long a FAILED_RETRYABLE task is kept for Orca to resume it. Longer than [completedTtlMs] because a
   * retryable task is expected to be picked up again.
   */
  var failedRetryableTtlMs: Long = TimeUnit.DAYS.toMillis(7)
  var batchSize: Int = 100
}
