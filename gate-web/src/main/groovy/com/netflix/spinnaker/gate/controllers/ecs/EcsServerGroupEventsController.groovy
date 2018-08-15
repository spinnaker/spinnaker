package com.netflix.spinnaker.gate.controllers.ecs

import com.netflix.spinnaker.gate.services.EcsServerGroupEventsService
import io.swagger.annotations.ApiOperation
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.web.bind.annotation.*

@RestController
class EcsServerGroupEventsController {

  @Autowired
  EcsServerGroupEventsService ecsServerGroupEventsService

  @ApiOperation(value = "Retrieves a list of events for a server group")
  @RequestMapping(value = "applications/{application}/serverGroups/{account}/{serverGroupName}/events", method = RequestMethod.GET)
  List getEvents(@PathVariable String application,
                 @PathVariable String account,
                 @PathVariable String serverGroupName,
                 @RequestParam(value = "region", required = true) String region,
                 @RequestParam(value = "provider", required = true) String provider) {
    ecsServerGroupEventsService.getServerGroupEvents(application, account, serverGroupName, region, provider)
  }
}
