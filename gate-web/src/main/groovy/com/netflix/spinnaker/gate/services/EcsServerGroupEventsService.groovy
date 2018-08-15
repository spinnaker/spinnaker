package com.netflix.spinnaker.gate.services

import com.netflix.spinnaker.gate.services.commands.HystrixFactory
import com.netflix.spinnaker.gate.services.internal.ClouddriverService
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

@Component
class EcsServerGroupEventsService {
  private static final String GROUP = "ecsServerGroupEvents"

  ClouddriverService clouddriver

  @Autowired
  EcsServerGroupEventsService(ClouddriverService clouddriver) {
    this.clouddriver = clouddriver
  }

  List getServerGroupEvents(String application, String account, String serverGroupName, String region, String cloudProvider) {
    HystrixFactory.newListCommand(GROUP, "getServerGroupEvents") {
      clouddriver.getServerGroupEvents(application, account, serverGroupName, region, cloudProvider)
    } execute()
  }
}
