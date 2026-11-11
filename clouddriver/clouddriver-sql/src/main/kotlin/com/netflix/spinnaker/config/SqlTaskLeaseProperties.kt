/*
 * Copyright 2026 Netflix, Inc.
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

import java.time.Duration
import java.util.concurrent.TimeUnit
import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * Per-instance execution leases for the SQL task repository, configured by
 * `sql.agent.task-lease`. See plans/2026/clouddriver-task-repository.md section 5a.
 */
@ConfigurationProperties("sql.agent.task-lease")
class SqlTaskLeaseProperties {
  /**
   * How often the instance renews its lease. The TTL is renewed from a background task
   * every [heartbeatIntervalMs]; the plan recommends a TTL of 2 min renewed every 20 s,
   * so a pod survives a couple of missed heartbeats before the reaper acts.
   */
  var heartbeatIntervalMs: Long = TimeUnit.SECONDS.toMillis(20)

  /**
   * How long a lease stays valid without a heartbeat. Set this longer than the SQL
   * failover window so a writer failover does not get pods declared dead mid-operation.
   */
  var leaseTtlMs: Long = TimeUnit.MINUTES.toMillis(2)

  /**
   * How long a gracefully stopping pod keeps running its in-flight operations before it
   * resolves them itself (with the same rules the reaper applies) and marks itself dead.
   */
  var shutdownWaitSeconds: Long = 30

  /** How many tasks of a dead instance the reaper resolves per batch. */
  var reaperBatchSize: Int = 100

  /** Extra duration added to [leaseTtlMs] when the pod stops gracefully, so the lease
   * outlives the [shutdownWaitSeconds] drain window and the pod marks itself dead rather
   * than being reaped mid-drain. */
  val drainLeaseExtension: Duration = Duration.ofSeconds(45)
}
