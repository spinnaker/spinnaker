/*
 * Copyright 2015 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.clouddriver.controllers

import com.netflix.spinnaker.clouddriver.model.ReservationReport
import com.netflix.spinnaker.clouddriver.model.ReservationReportProvider
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestMethod
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/reports/reservation")
class ReservationReportController {
  @Autowired
  List<ReservationReportProvider> reservationProviders

  @RequestMapping(method = RequestMethod.GET)
  Collection<ReservationReport> getReservationReports() {
    reservationProviders.collect { it.getReservationReport("v1") } - null
  }

  @RequestMapping(method = RequestMethod.GET, value = "/{name}")
  Collection<ReservationReport> getReservationReportsByName(@PathVariable String name) {
    reservationProviders.collect { it.getReservationReport(name) } - null
  }
}
