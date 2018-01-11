package com.netflix.spinnaker.gate.controllers.ecs

import com.netflix.spinnaker.gate.services.EcsClusterService
import io.swagger.annotations.ApiOperation
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestMethod
import org.springframework.web.bind.annotation.RestController

@RestController
class EcsClusterController {
  @Autowired
  EcsClusterService ecsClusterService

  @ApiOperation(value = "Retrieve a list of ECS clusters that can be used for the account and region.")
  @RequestMapping(value = "/ecs/ecsClusters", method = RequestMethod.GET)
  List all() {
    ecsClusterService.getAllEcsClusters()
  }
}
