/*
 * Copyright 2014 Netflix, Inc.
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

package com.netflix.spinnaker.oort.controllers.aws

import com.amazonaws.services.elasticloadbalancing.model.LoadBalancerDescription
import com.fasterxml.jackson.annotation.JsonProperty
import com.netflix.spinnaker.oort.data.aws.Keys
import com.netflix.spinnaker.oort.model.CacheService
import com.netflix.spinnaker.oort.model.aws.AmazonLoadBalancer
import groovy.transform.CompileStatic
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestMethod
import org.springframework.web.bind.annotation.RestController

@CompileStatic
@RestController
@RequestMapping("/aws/loadBalancers")
class AmazonLoadBalancerController {

  @Autowired
  CacheService cacheService

  @RequestMapping(method = RequestMethod.GET)
  List<AmazonLoadBalancerSummary> list() {
    getSummaryForKeys(cacheService.keysByType(Keys.Namespace.LOAD_BALANCER_SERVER_GROUPS)).values() as List
  }

  @RequestMapping(value = "/{name}", method = RequestMethod.GET)
  AmazonLoadBalancerSummary get(@PathVariable String name) {
    def keys = cacheService.keysByType(Keys.Namespace.LOAD_BALANCER_SERVER_GROUPS).findAll { key ->
      def parts = key.split(':')
      def elbName = parts[1]
      elbName == name
    }
    getSummaryForKeys(keys)?.get(name)
  }

  private Map<String, AmazonLoadBalancerSummary> getSummaryForKeys(Collection<String> keys) {
    Map<String, AmazonLoadBalancerSummary> map = [:]
    for (key in keys) {
      def parts = key.split(':')
      def name = parts[1]
      def account = parts[2]
      def region = parts[3]
      def serverGroupName = parts[4]
      def summary = map.get(name)
      if (!summary) {
        summary = new AmazonLoadBalancerSummary(name: name)
        map.put name, summary
      }

      def loadBalancer = new AmazonLoadBalancer(name, region)
      loadBalancer.elb = cacheService.retrieve(Keys.getLoadBalancerKey(name, account, region), LoadBalancerDescription)
      if (!loadBalancer.serverGroups.contains(serverGroupName)) {
        loadBalancer.serverGroups << serverGroupName
      }
      summary.getOrCreateAccount(account).getOrCreateRegion(region).loadBalancers << loadBalancer
    }
    map
  }

  // view models...

  static class AmazonLoadBalancerSummary {
    private Map<String, AmazonLoadBalancerAccount> mappedAccounts = [:]
    String name

    AmazonLoadBalancerAccount getOrCreateAccount(String name) {
      if (!mappedAccounts.containsKey(name)) {
        mappedAccounts.put(name, new AmazonLoadBalancerAccount(name: name))
      }
      mappedAccounts[name]
    }

    @JsonProperty("accounts")
    List<AmazonLoadBalancerAccount> getAccounts() {
      mappedAccounts.values() as List
    }
  }

  static class AmazonLoadBalancerAccount {
    private Map<String, AmazonLoadBalancerAccountRegion> mappedRegions = [:]
    String name

    AmazonLoadBalancerAccountRegion getOrCreateRegion(String name) {
      if (!mappedRegions.containsKey(name)) {
        mappedRegions.put(name, new AmazonLoadBalancerAccountRegion(name: name, loadBalancers: []))
      }
      mappedRegions[name]
    }

    @JsonProperty("regions")
    List<AmazonLoadBalancerAccountRegion> getRegions() {
      mappedRegions.values() as List
    }
  }

  static class AmazonLoadBalancerAccountRegion {
    String name
    List<AmazonLoadBalancer> loadBalancers
  }
}
