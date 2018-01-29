package com.netflix.spinnaker.gate.controllers.ecs

import com.netflix.spinnaker.gate.services.EcsCloudMetricService
import io.swagger.annotations.ApiOperation
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestMethod
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/ecs/cloudMetrics")
class EcsCloudMetricController {
  @Autowired
  EcsCloudMetricService ecsClusterService

  @ApiOperation(value = "Retrieve a list of MetricAlarms.")
  @RequestMapping(value = "/alarms", method = RequestMethod.GET)
  List allMetricAlarms() {
    ecsClusterService.getEcsAllMetricAlarms()
  }
}
