package com.netflix.spinnaker.gate.services

import com.netflix.spinnaker.gate.services.commands.HystrixFactory
import com.netflix.spinnaker.gate.services.internal.ClouddriverService
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

@Component
class EcsSecretService {
  private static final String GROUP = "ecsSecretService"

  ClouddriverService clouddriver

  @Autowired
  EcsSecretService(ClouddriverService clouddriver) {
    this.clouddriver = clouddriver
  }

  List getAllEcsSecrets() {
    HystrixFactory.newListCommand(GROUP, "getAllEcsSecrets") {
      clouddriver.getAllEcsSecrets()
    } execute()
  }
}
