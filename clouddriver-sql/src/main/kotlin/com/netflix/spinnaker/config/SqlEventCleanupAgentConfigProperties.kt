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
package com.netflix.spinnaker.config

import java.time.Duration
import javax.validation.constraints.Positive
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.validation.annotation.Validated

@Validated
@ConfigurationProperties("spinnaker.clouddriver.eventing.cleanup-agent")
class SqlEventCleanupAgentConfigProperties {
  /**
   * The frequency, in milliseconds, of how often the cleanup agent will run. Defaults to 30 minutes.
   */
  var frequency: Duration = Duration.ofMinutes(30)

  /**
   * The ceiling execution time that the agent should be allowed to run before it will be timed out and available for
   * reschedule onto a different Clouddriver instance. Defaults to 1 hour.
   */
  var timeout: Duration = Duration.ofHours(1)

  /**
   * The max age of an [Aggregate]. Defaults to 7 days.
   */
  @Positive
  var maxAggregateAgeDays: Long = 7

  /**
   * The max number of events to cleanup in each agent invocation.
   */
  @Positive
  var cleanupLimit: Int = EVENT_CLEANUP_LIMIT

  companion object {
    const val EVENT_CLEANUP_LIMIT = 100_000
  }
}
