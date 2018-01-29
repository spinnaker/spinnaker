package com.netflix.spinnaker.gate.services

import com.netflix.spinnaker.gate.services.commands.HystrixFactory
import com.netflix.spinnaker.gate.services.internal.ClouddriverService
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

@Component
class EcsCloudMetricService {
  private static final String GROUP = "ecsCloudMetricService"

  ClouddriverService clouddriver

  @Autowired
  EcsCloudMetricService(ClouddriverService clouddriver) {
    this.clouddriver = clouddriver
  }

  List getEcsAllMetricAlarms() {
    HystrixFactory.newListCommand(GROUP, "getEcsAllMetricAlarms") {
      clouddriver.getEcsAllMetricAlarms()
    } execute()
  }
}
