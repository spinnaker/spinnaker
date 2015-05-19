package com.netflix.spinnaker.gate.services

import com.netflix.spinnaker.gate.services.commands.HystrixFactory
import com.netflix.spinnaker.gate.services.internal.OortService
import groovy.transform.CompileStatic
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

@CompileStatic
@Component
class ReportService {
  private static final String GROUP = "reports"

  @Autowired
  OortService oortService

  List<Map> getReservationReports() {
    HystrixFactory.newListCommand(GROUP, "getReservationReports", true) {
      oortService.getReservationReports()
    } execute()
  }
}
