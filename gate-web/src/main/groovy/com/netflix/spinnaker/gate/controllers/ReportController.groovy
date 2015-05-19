package com.netflix.spinnaker.gate.controllers

import com.netflix.spinnaker.gate.services.ReportService
import groovy.transform.CompileStatic
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestMethod
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@CompileStatic
@RequestMapping("/reports")
@RestController
class ReportController {
  @Autowired
  ReportService reportService

  @RequestMapping(value = "/reservation", method = RequestMethod.GET)
  Map getReservationReport(@RequestParam(value = "provider", defaultValue = "aws", required = false) String provider) {
    reportService.getReservationReports().find { it["type"] == provider }
  }
}
