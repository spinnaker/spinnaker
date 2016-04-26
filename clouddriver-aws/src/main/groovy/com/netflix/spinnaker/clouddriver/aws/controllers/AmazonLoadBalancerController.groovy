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

package com.netflix.spinnaker.clouddriver.aws.controllers

import com.fasterxml.jackson.annotation.JsonProperty
import com.netflix.spinnaker.cats.cache.Cache
import com.netflix.spinnaker.cats.cache.CacheData
import com.netflix.spinnaker.cats.cache.RelationshipCacheFilter
import com.netflix.spinnaker.clouddriver.aws.data.Keys
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestMethod
import org.springframework.web.bind.annotation.RestController

import static com.netflix.spinnaker.clouddriver.core.provider.agent.Namespace.LOAD_BALANCERS

@RestController
@RequestMapping("/aws/loadBalancers")
class AmazonLoadBalancerController {

  private final Cache cacheView

  @Autowired
  AmazonLoadBalancerController(Cache cacheView) {
    this.cacheView = cacheView
  }

  @RequestMapping(method = RequestMethod.GET)
  List<AmazonLoadBalancerSummary> list() {
    def searchKey = Keys.getLoadBalancerKey('*', '*', '*', null) + '*'
    Collection<String> identifiers = cacheView.filterIdentifiers(LOAD_BALANCERS.ns, searchKey)
    getSummaryForLoadBalancers(identifiers).values() as List
  }

  @RequestMapping(value = "/{name:.+}", method = RequestMethod.GET)
  AmazonLoadBalancerSummary get(@PathVariable String name) {
    def searchKey = Keys.getLoadBalancerKey(name, '*', '*', null)  + "*"
    Collection<String> identifiers = cacheView.filterIdentifiers(LOAD_BALANCERS.ns, searchKey).findAll {
      def key = Keys.parse(it)
      key.loadBalancer == name
    }
    getSummaryForLoadBalancers(identifiers).get(name)
  }

  @RequestMapping(value = "/{account}/{region}", method = RequestMethod.GET)
  List<AmazonLoadBalancerSummary> getInAccountAndRegion(@PathVariable String account, @PathVariable String region) {
    def searchKey = Keys.getLoadBalancerKey('*', account, region, null)
    Collection<String> identifiers = cacheView.filterIdentifiers(LOAD_BALANCERS.ns, searchKey)
    getSummaryForLoadBalancers(identifiers).values() as List
  }

  @RequestMapping(value = "/{account}/{region}/{name:.+}", method = RequestMethod.GET)
  List<Map> getDetailsInAccountAndRegionByName(@PathVariable String account, @PathVariable String region, @PathVariable String name) {
    def searchKey = Keys.getLoadBalancerKey(name, account, region, null) + '*'
    Collection<String> identifiers = cacheView.filterIdentifiers(LOAD_BALANCERS.ns, searchKey).findAll {
      Keys.parse(it).loadBalancer == name
    }

    cacheView.getAll(LOAD_BALANCERS.ns, identifiers).attributes
  }

  @RequestMapping(value = "/{account}/{region}/{name}/{vpcId}", method = RequestMethod.GET)
  Map getDetailsInAccountAndRegionByName(@PathVariable String account, @PathVariable String region, @PathVariable String name, @PathVariable String vpcId) {
    def key = Keys.getLoadBalancerKey(name, account, region, vpcId)
    cacheView.get(LOAD_BALANCERS.ns, key)?.attributes
  }

  private Map<String, AmazonLoadBalancerSummary> getSummaryForLoadBalancers(Collection<String> loadBalancerKeys) {
    Map<String, AmazonLoadBalancerSummary> map = [:]
    Map<String, CacheData> loadBalancers = cacheView.getAll(LOAD_BALANCERS.ns, loadBalancerKeys, RelationshipCacheFilter.none()).collectEntries { [(it.id): it] }
    for (lb in loadBalancerKeys) {
      CacheData loadBalancerFromCache = loadBalancers[lb]
      if (loadBalancerFromCache) {
        def parts = Keys.parse(lb)
        String name = parts.loadBalancer
        String region = parts.region
        String account = parts.account
        def summary = map.get(name)
        if (!summary) {
          summary = new AmazonLoadBalancerSummary(name: name)
          map.put name, summary
        }
        def loadBalancer = new AmazonLoadBalancerDetail()
        loadBalancer.account = parts.account
        loadBalancer.region = parts.region
        loadBalancer.name = parts.loadBalancer
        loadBalancer.vpcId = parts.vpcId
        loadBalancer.securityGroups = loadBalancerFromCache.attributes.securityGroups

        summary.getOrCreateAccount(account).getOrCreateRegion(region).loadBalancers << loadBalancer
      }
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
    List<AmazonLoadBalancerSummary> loadBalancers
  }

  static class AmazonLoadBalancerDetail {
    String account
    String region
    String name
    String vpcId
    String type = 'aws'
    List<String> securityGroups = []
  }
}
