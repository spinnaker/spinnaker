/*
 * Copyright 2015 Pivotal, Inc.
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

package com.netflix.spinnaker.clouddriver.cf.controllers

import com.fasterxml.jackson.annotation.JsonProperty
import com.netflix.spinnaker.clouddriver.security.AccountCredentialsProvider
import com.netflix.spinnaker.clouddriver.cf.model.CloudFoundryLoadBalancer
import com.netflix.spinnaker.clouddriver.cf.model.CloudFoundryResourceRetriever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestMethod
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/cf/loadBalancers")
class CloudFoundryLoadBalancerController {

  @Autowired
  AccountCredentialsProvider accountCredentialsProvider

  @Autowired
  CloudFoundryResourceRetriever cfResourceRetriever

  @RequestMapping(method = RequestMethod.GET)
  List<CloudFoundryLoadBalancerAccount> list() {
    getSummaryForLoadBalancers(cfResourceRetriever.loadBalancersByAccount).values() as List
  }

  @RequestMapping(value = "/{name:.+}", method = RequestMethod.GET)
  CloudFoundryLoadBalancerSummary get(@PathVariable String name) {
    getSummaryForLoadBalancers(cfResourceRetriever.loadBalancersByAccount).get(name)
  }

  @RequestMapping(value = "/{account}/{region}/{name:.+}", method = RequestMethod.GET)
  List<Map> getDetailsInAccountAndRegionByName(@PathVariable String account, @PathVariable String region, @PathVariable String name) {
    def accountMap = cfResourceRetriever.loadBalancersByAccount[account]

    if (accountMap) {
      def regionList = accountMap

      if (regionList) {
        def cfLoadBalancer = regionList.find {
          it.name == name
        }

        if (cfLoadBalancer) {
          def loadBalancerDetail = [
            loadBalancerName: name
          ]

          loadBalancerDetail.ipAddress = cfLoadBalancer.nativeRoute.host + '.' + cfLoadBalancer.nativeRoute.domain.name
          loadBalancerDetail.dnsname = cfLoadBalancer.nativeRoute.host + '.' + cfLoadBalancer.nativeRoute.domain.name

          return [ loadBalancerDetail ]
        }
      }
    }

    return []
  }

  public static Map<String, CloudFoundryLoadBalancerSummary> getSummaryForLoadBalancers(
      Map<String, Set<CloudFoundryLoadBalancer>> networkLoadBalancerMap) {
    Map<String, CloudFoundryLoadBalancerSummary> map = [:]

    networkLoadBalancerMap?.each() { account, loadBalancerList ->
      loadBalancerList.each { CloudFoundryLoadBalancer loadBalancer ->
        def summary = map.get(loadBalancer.name)

        if (!summary) {
          summary = new CloudFoundryLoadBalancerSummary(name: loadBalancer.name)
          map.put loadBalancer.name, summary
        }

        def loadBalancerDetail =
          new CloudFoundryLoadBalancerDetail(account: account, region: 'unknown', name: loadBalancer.name)

        //summary.getOrCreateAccount(account).getOrCreateRegion(region).loadBalancers << loadBalancerDetail
      }
    }

    map
  }

  static class CloudFoundryLoadBalancerSummary {
    private Map<String, CloudFoundryLoadBalancerAccount> mappedAccounts = [:]
    String name

    CloudFoundryLoadBalancerAccount getOrCreateAccount(String name) {
      if (!mappedAccounts.containsKey(name)) {
        mappedAccounts.put(name, new CloudFoundryLoadBalancerAccount(name: name))
      }
      mappedAccounts[name]
    }

    @JsonProperty("accounts")
    List<CloudFoundryLoadBalancerAccount> getAccounts() {
      mappedAccounts.values() as List
    }
  }

  static class CloudFoundryLoadBalancerAccount {
    private Map<String, CloudFoundryLoadBalancerAccountRegion> mappedRegions = [:]
    String name

    CloudFoundryLoadBalancerAccountRegion getOrCreateRegion(String name) {
      if (!mappedRegions.containsKey(name)) {
        mappedRegions.put(name, new CloudFoundryLoadBalancerAccountRegion(name: name, loadBalancers: []))
      }
      mappedRegions[name]
    }

    @JsonProperty("regions")
    List<CloudFoundryLoadBalancerAccountRegion> getRegions() {
      mappedRegions.values() as List
    }
  }

  static class CloudFoundryLoadBalancerAccountRegion {
    String name
    List<CloudFoundryLoadBalancerDetail> loadBalancers
  }

  static class CloudFoundryLoadBalancerDetail {
    String account
    String region
    String name
    String type = 'cf'
  }
}
