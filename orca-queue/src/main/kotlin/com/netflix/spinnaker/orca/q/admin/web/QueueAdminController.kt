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

import com.netflix.spinnaker.orca.q.admin.HydrateQueueCommand
import com.netflix.spinnaker.orca.q.admin.HydrateQueueInput
import com.netflix.spinnaker.orca.q.admin.HydrateQueueOutput
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestMethod
import org.springframework.web.bind.annotation.RestController
import java.time.Instant
import javax.ws.rs.QueryParam

@RestController
@RequestMapping("/admin/queue")
class QueueAdminController(
  private val hydrateCommand: HydrateQueueCommand
) {

  @RequestMapping(value = ["/hydrate"], method = [(RequestMethod.POST)])
  fun hydrateQueue(
    @QueryParam("dryRun") dryRun: Boolean?,
    @QueryParam("executionId") executionId: String?,
    @QueryParam("startMs") startMs: Long?,
    @QueryParam("endMs") endMs: Long?
  ): HydrateQueueOutput =
    hydrateCommand(HydrateQueueInput(
      executionId,
      if (startMs != null) Instant.ofEpochMilli(startMs) else null,
      if (endMs != null) Instant.ofEpochMilli(endMs) else null,
      dryRun ?: true
    ))
}
