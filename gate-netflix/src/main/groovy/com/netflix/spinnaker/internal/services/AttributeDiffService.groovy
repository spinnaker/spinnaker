package com.netflix.spinnaker.internal.services

import com.netflix.spinnaker.gate.services.commands.HystrixFactory
import com.netflix.spinnaker.internal.services.internal.TideService
import groovy.transform.CompileStatic
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

@CompileStatic
@Component
class AttributeDiffService {
  private static final String GROUP = "attributeDiff"

  @Autowired TideService tideService

  Map getServerGroupDiff(String account, String clusterName) {
    HystrixFactory.newMapCommand(GROUP, "getServerGroupDiff") {
      tideService.getServerGroupDiff(account, clusterName)
    } execute()
  }
}
