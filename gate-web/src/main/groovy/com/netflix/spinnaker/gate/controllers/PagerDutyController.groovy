/*
 * Copyright 2018 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.gate.controllers

import com.netflix.spinnaker.gate.services.PagerDutyService
import com.netflix.spinnaker.security.AuthenticatedRequest
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import retrofit.RetrofitError

import java.util.concurrent.atomic.AtomicReference

@CompileStatic
@RequestMapping("/pagerDuty")
@RestController
@ConditionalOnProperty('pager-duty.token')
@Slf4j
class PagerDutyController {
  AtomicReference<List<Map>> pagerDutyServicesCache = new AtomicReference<>([])
  AtomicReference<List<Map>> pagerDutyOnCallCache = new AtomicReference<>([])

  @Autowired
  PagerDutyService pagerDutyService

  @GetMapping("/services")
  List<Map> getServices() {
    return pagerDutyServicesCache.get().collect {
      def policyId = it.escalation_policy != null ? it["escalation_policy"]["id"] : null
      [
        status: it["status"],
        integration_key: pluckIntegrationKey(it as Map),
        lastIncidentTimestamp: it["last_incident_timestamp"],
        name: it["name"],
        id: it["id"],
        policy: policyId,
      ]
    }.flatten() as List<Map>
  }

  String pluckIntegrationKey(Map service) {
    return (service.integrations as List<Map>).find { it.type == "generic_events_api_inbound_integration" }?.integration_key
  }

  @GetMapping("/oncalls")
  Map<String, List<Map>> getOnCalls() {
    // Map by escalation policy
    Map<String, List<Map>> groupedByPolicy = pagerDutyOnCallCache.get().groupBy { (it.escalation_policy as Map)?.id as String }
    groupedByPolicy.each {
      it.value.sort { v -> (v as Map).escalation_level }
    }

    return groupedByPolicy
  }


  @Scheduled(fixedDelay = 300000L)
  void refreshPagerDuty() {
    try {
      List<Map> services = fetchAllServices()
      log.info("Fetched {} PagerDuty services", services?.size())
      pagerDutyServicesCache.set(services)
    } catch (e) {
      if (e instanceof RetrofitError && e.response?.status == 429) {
        log.warn("Unable to refresh PagerDuty service list (throttled!)")
      } else {
        log.error("Unable to refresh PagerDuty service list", e)
      }
    }

    try {
      List<Map> onCalls = fetchAllOnCalls()
      log.info("Fetched {} PagerDuty onCall", onCalls?.size())
      pagerDutyOnCallCache.set(onCalls)
    } catch (e) {
      if (e instanceof RetrofitError && e.response?.status == 429) {
        log.warn("Unable to refresh PagerDuty onCall list (throttled!)")
      } else {
        log.error("Unable to refresh PagerDuty onCall list", e)
      }
    }
  }

  List<Map> fetchAllServices() {
    PagerDutyService.PagerDutyServiceResult response = AuthenticatedRequest.allowAnonymous { pagerDutyService.getServices(0) }
    List<Map> services = response?.services
    while (response?.more) {
      response = AuthenticatedRequest.allowAnonymous { pagerDutyService.getServices(services.size()) }
      services += response?.services
    }
    return services
  }

  List<Map> fetchAllOnCalls() {
    PagerDutyService.PagerDutyOnCallResult response = AuthenticatedRequest.allowAnonymous { pagerDutyService.getOnCalls(0) }
    List<Map> onCalls = response?.oncalls
    while (response?.more) {
      response = AuthenticatedRequest.allowAnonymous { pagerDutyService.getOnCalls(onCalls.size()) }
      onCalls += response?.oncalls
    }
    return onCalls
  }
}
