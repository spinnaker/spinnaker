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
import com.netflix.spinnaker.oort.gce.model.GoogleResourceRetriever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestMethod
import org.springframework.web.bind.annotation.RestController

import javax.annotation.PostConstruct

@RestController
@RequestMapping("/gce/loadBalancers")
class GoogleLoadBalancerController {

  @Autowired
  AccountCredentialsProvider accountCredentialsProvider

  GoogleResourceRetriever googleResourceRetriever

  @PostConstruct
  void init() {
    googleResourceRetriever = new GoogleResourceRetriever()
    googleResourceRetriever.init(accountCredentialsProvider)
  }

  @RequestMapping(method = RequestMethod.GET)
  List<GoogleLoadBalancerAccount> list() {
    getSummaryForLoadBalancers(googleResourceRetriever.getNetworkLoadBalancerMap())
  }

  public static List<GoogleLoadBalancerAccount> getSummaryForLoadBalancers(
    Map<String, Map<String, List<String>>> networkLoadBalancerMap) {
    List<GoogleLoadBalancerAccount> list = []

    networkLoadBalancerMap?.each() { account, regionMap ->
      def loadBalancerAccount = list.find {
        it.name == account
      }

      if (!loadBalancerAccount) {
        loadBalancerAccount = new GoogleLoadBalancerAccount(account: account)
        list << loadBalancerAccount
      }

      regionMap.each() { region, loadBalancerList ->
        loadBalancerAccount.getOrCreateRegion(region).loadBalancers.with { loadBalancers ->
          loadBalancerList.each { loadBalancer ->
            loadBalancers << new GoogleLoadBalancerDetail(account: account, region: region, name: loadBalancer)
          }
        }
      }
    }

    list
  }

  static class GoogleLoadBalancerAccount {
    private Map<String, GoogleLoadBalancerAccountRegion> mappedRegions = [:]
    String account

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
