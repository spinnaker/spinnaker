/*
 * Copyright 2018 Lookout, Inc.
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

package com.netflix.spinnaker.gate.controllers.ecs

import com.netflix.spinnaker.gate.services.EcsServerGroupEventsService
import io.swagger.v3.oas.annotations.Operation
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.web.bind.annotation.*

@RestController
class EcsServerGroupEventsController {

  @Autowired
  EcsServerGroupEventsService ecsServerGroupEventsService

  @Operation(summary = "Retrieves a list of events for a server group")
  @RequestMapping(value = "applications/{application}/serverGroups/{account}/{serverGroupName}/events", method = RequestMethod.GET)
  List getEvents(@PathVariable String application,
                 @PathVariable String account,
                 @PathVariable String serverGroupName,
                 @RequestParam(value = "region", required = true) String region,
                 @RequestParam(value = "provider", required = true) String provider) {
    ecsServerGroupEventsService.getServerGroupEvents(application, account, serverGroupName, region, provider)
  }
}
