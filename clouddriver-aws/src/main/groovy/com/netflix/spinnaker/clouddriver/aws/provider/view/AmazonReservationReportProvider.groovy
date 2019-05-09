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

package com.netflix.spinnaker.clouddriver.aws.provider.view

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.cats.cache.Cache
import com.netflix.spinnaker.clouddriver.model.ReservationReport
import com.netflix.spinnaker.clouddriver.model.ReservationReportProvider
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Component

import static com.netflix.spinnaker.clouddriver.core.provider.agent.Namespace.RESERVATION_REPORTS

@Component
class AmazonReservationReportProvider implements ReservationReportProvider {
  @Autowired
  Cache cacheView

  @Autowired
  @Qualifier("amazonObjectMapper")
  ObjectMapper objectMapper

  @Override
  ReservationReport getReservationReport(String name, Map<String, String> filters) {
    def cacheData = cacheView.get(RESERVATION_REPORTS.ns, name)
    if (!cacheData) {
      return null
    }

    def reservationReport = objectMapper.readValue(
      objectMapper.writeValueAsString(cacheData.attributes.report),
      MapBackedReservationReport
    )

    def instanceFamily = filters.instanceFamily
    def region = filters.region
    def os = filters.os

    reservationReport.reservations = reservationReport.reservations.findAll { Map reservation ->
      def shouldKeep = true

      if (instanceFamily && reservation.instanceType) {
        shouldKeep = shouldKeep && reservation.instanceType.toString().toLowerCase().startsWith(instanceFamily)
      }

      if (region && reservation.region) {
        shouldKeep = shouldKeep && region.equalsIgnoreCase(reservation.region.toString())
      }

      if (os && reservation.os) {
        shouldKeep = shouldKeep && os.equalsIgnoreCase(reservation.os.toString())
      }

      return shouldKeep
    }

    return reservationReport
  }

  static class MapBackedReservationReport extends HashMap implements ReservationReport {
    // deserialize directly into a map avoids default values for AccountReservationDetail.[reservedVpc|usedVpc]
  }
}

