/*
 * Copyright 2014 Google, Inc.
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

package com.netflix.spinnaker.oort.gce.controllers

import com.fasterxml.jackson.annotation.JsonProperty
import com.netflix.spinnaker.amos.AccountCredentialsProvider
import com.netflix.spinnaker.oort.gce.model.GoogleLoadBalancer
import com.netflix.spinnaker.oort.gce.model.GoogleResourceRetriever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestMethod
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/gce/loadBalancers")
class GoogleLoadBalancerController {

  @Autowired
  AccountCredentialsProvider accountCredentialsProvider

  @Autowired
  GoogleResourceRetriever googleResourceRetriever

  @RequestMapping(method = RequestMethod.GET)
  List<GoogleLoadBalancerAccount> list() {
    getSummaryForLoadBalancers(googleResourceRetriever.getNetworkLoadBalancerMap()).values() as List
  }

  @RequestMapping(value = "/{name:.+}", method = RequestMethod.GET)
  GoogleLoadBalancerSummary get(@PathVariable String name) {
    getSummaryForLoadBalancers(googleResourceRetriever.getNetworkLoadBalancerMap()).get(name)
  }

  @RequestMapping(value = "/{account}/{region}/{name:.+}", method = RequestMethod.GET)
  List<Map> getDetailsInAccountAndRegionByName(@PathVariable String account, @PathVariable String region, @PathVariable String name) {
    Map<String, Map<String, List<GoogleLoadBalancer>>> networkLoadBalancerMap =
      googleResourceRetriever.getNetworkLoadBalancerMap()
    def accountMap = networkLoadBalancerMap[account]

    if (accountMap) {
      def regionList = accountMap[region]

      if (regionList) {
        def googleLoadBalancer = regionList.find {
          it.name == name
        }

        if (googleLoadBalancer) {
          def loadBalancerDetail = [
            loadBalancerName: name
          ]

          if (googleLoadBalancer.createdTime) {
            loadBalancerDetail.createdTime = googleLoadBalancer.createdTime
          }

          // TODO(duftler): This displays well in the details pane in deck, but the port-range values cannot be edited.
          // This is ok for now, since we don't support Create/Edit of load balancers via Deck yet anyway.
          // Revisit this when we have a GCE-specific details pane in deck.
          loadBalancerDetail.listenerDescriptions = [
            [
              listener: [
                protocol: googleLoadBalancer.ipProtocol,
                loadBalancerPort: googleLoadBalancer.portRange,
                instanceProtocol: googleLoadBalancer.ipProtocol,
                instancePort: googleLoadBalancer.portRange
              ]
            ]
          ]

          if (googleLoadBalancer.healthCheck) {
            loadBalancerDetail.healthCheck = googleLoadBalancer.healthCheck
          }

          loadBalancerDetail.ipAddress = googleLoadBalancer.ipAddress

          // TODO(duftler): Remove this when we have a GCE-specific details pane in deck.
          loadBalancerDetail.dnsname = googleLoadBalancer.ipAddress

          return [ loadBalancerDetail ]
        }
      }
    }

    return []
  }

  public static Map<String, GoogleLoadBalancerSummary> getSummaryForLoadBalancers(
    Map<String, Map<String, List<GoogleLoadBalancer>>> networkLoadBalancerMap) {
    Map<String, GoogleLoadBalancerSummary> map = [:]

    networkLoadBalancerMap?.each() { account, regionMap ->
      regionMap.each() { region, loadBalancerList ->
        loadBalancerList.each { GoogleLoadBalancer loadBalancer ->
          def summary = map.get(loadBalancer.name)

          if (!summary) {
            summary = new GoogleLoadBalancerSummary(name: loadBalancer.name)
            map.put loadBalancer.name, summary
          }

          def loadBalancerDetail =
            new GoogleLoadBalancerDetail(account: account, region: region, name: loadBalancer.name)

          summary.getOrCreateAccount(account).getOrCreateRegion(region).loadBalancers << loadBalancerDetail
        }
      }
    }

    map
  }

  static class GoogleLoadBalancerSummary {
    private Map<String, GoogleLoadBalancerAccount> mappedAccounts = [:]
    String name

    GoogleLoadBalancerAccount getOrCreateAccount(String name) {
      if (!mappedAccounts.containsKey(name)) {
        mappedAccounts.put(name, new GoogleLoadBalancerAccount(name: name))
      }
      mappedAccounts[name]
    }

    @JsonProperty("accounts")
    List<GoogleLoadBalancerAccount> getAccounts() {
      mappedAccounts.values() as List
    }
  }

  static class GoogleLoadBalancerAccount {
    private Map<String, GoogleLoadBalancerAccountRegion> mappedRegions = [:]
    String name

    GoogleLoadBalancerAccountRegion getOrCreateRegion(String name) {
      if (!mappedRegions.containsKey(name)) {
        mappedRegions.put(name, new GoogleLoadBalancerAccountRegion(name: name, loadBalancers: []))
      }
      mappedRegions[name]
    }

    @JsonProperty("regions")
    List<GoogleLoadBalancerAccountRegion> getRegions() {
      mappedRegions.values() as List
    }
  }

  static class GoogleLoadBalancerAccountRegion {
    String name
    List<GoogleLoadBalancerDetail> loadBalancers
  }

  static class GoogleLoadBalancerDetail {
    String account
    String region
    String name
    String type = 'gce'
  }
}
