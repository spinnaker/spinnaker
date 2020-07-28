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
package com.netflix.spinnaker.orca.q.admin.web

import com.netflix.spinnaker.orca.q.StartWaitingExecutions
import com.netflix.spinnaker.orca.q.admin.HydrateQueueCommand
import com.netflix.spinnaker.orca.q.admin.HydrateQueueInput
import com.netflix.spinnaker.orca.q.admin.HydrateQueueOutput
import com.netflix.spinnaker.q.Message
import com.netflix.spinnaker.q.Queue
import java.time.Instant
import javax.ws.rs.QueryParam
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/admin/queue")
class QueueAdminController(
  private val hydrateCommand: HydrateQueueCommand,
  private val queue: Queue
) {

  @PostMapping(value = ["/hydrate"])
  fun hydrateQueue(
    @QueryParam("dryRun") dryRun: Boolean?,
    @QueryParam("executionId") executionId: String?,
    @QueryParam("startMs") startMs: Long?,
    @QueryParam("endMs") endMs: Long?
  ): HydrateQueueOutput =
    hydrateCommand(
      HydrateQueueInput(
        executionId,
        if (startMs != null) Instant.ofEpochMilli(startMs) else null,
        if (endMs != null) Instant.ofEpochMilli(endMs) else null,
        dryRun ?: true
      )
    )

  /**
   * Posts StartWaitingExecutions message for the given pipeline message into the queue.
   * This is useful when doing DB migration. If an execution is running from an old DB
   * and a new execution is queue in the new DB it will be correctly buffered/pended.
   * However, the old orca instance (pointed to old DB) won't know about these pending
   * executions and thus won't kick them off. To the user this looks like an execution
   * that will never start.
   */
  @PostMapping(value = ["kickPending"])
  fun kickPendingExecutions(
    @QueryParam("pipelineConfigId") pipelineConfigId: String,
    @QueryParam("purge") purge: Boolean?
  ) {

    queue.push(StartWaitingExecutions(pipelineConfigId, purge ?: false))
  }

  /**
   * Push any message into the queue.
   *
   * Note: you must specify the message type in the body to ensure it gets parse correctly, e.g.:
   *
   * {
   *   "kind": "startWaitingExecutions",
   *   "pipelineConfigId": "1acd0724-082e-4b8d-a987-9df7f8baf03f",
   *   "purge": false
   * }
   */
  @PostMapping(value = ["pushMessage"])
  fun pushMessageIntoQueue(
    @RequestBody message: Message
  ) {
    queue.push(message)
  }
}
