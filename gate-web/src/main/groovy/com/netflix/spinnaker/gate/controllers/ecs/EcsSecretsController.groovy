package com.netflix.spinnaker.gate.controllers.ecs

import com.netflix.spinnaker.gate.services.EcsSecretService
import io.swagger.annotations.ApiOperation
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestMethod
import org.springframework.web.bind.annotation.RestController

@RestController
class EcsSecretController {
  @Autowired
  EcsSecretService ecsSecretService

  @ApiOperation(value = "Retrieve a list of Secrets Manager secrets that can be used for the account and region.")
  @RequestMapping(value = "/ecs/secrets", method = RequestMethod.GET)
  List all() {
    ecsSecretService.getAllEcsSecrets()
  }
}
