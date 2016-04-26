/*
 * Copyright 2015-2016 Pivotal, Inc.
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
import com.netflix.spinnaker.cats.cache.Cache
import com.netflix.spinnaker.cats.cache.CacheData
import com.netflix.spinnaker.cats.cache.RelationshipCacheFilter
import com.netflix.spinnaker.clouddriver.cf.cache.Keys
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestMethod
import org.springframework.web.bind.annotation.RestController

import static com.netflix.spinnaker.clouddriver.cf.cache.Keys.Namespace.LOAD_BALANCERS

@RestController
@RequestMapping("/cf/loadBalancers")
class CloudFoundryLoadBalancerController {

  private final Cache cacheView

  @Autowired
  CloudFoundryLoadBalancerController(Cache cacheView) {
    this.cacheView = cacheView
  }

  @RequestMapping(method = RequestMethod.GET)
  List<CloudFoundryLoadBalancerAccount> list() {
    def searchKey = Keys.getLoadBalancerKey('*', '*', '*')
    Collection<String> identifiers = cacheView.filterIdentifiers(LOAD_BALANCERS.ns, searchKey)
    getSummaryForLoadBalancers(identifiers).values() as List
  }

  @RequestMapping(value = "/{name:.+}", method = RequestMethod.GET)
  CloudFoundryLoadBalancerSummary get(@PathVariable String name) {
    def searchKey = Keys.getLoadBalancerKey(name, '*', '*')
    Collection<String> identifiers = cacheView.filterIdentifiers(LOAD_BALANCERS.ns, searchKey)
    getSummaryForLoadBalancers(identifiers).get(name)
  }

  @RequestMapping(value = "/{account}/{region}/{name:.+}", method = RequestMethod.GET)
  List<Map> getDetailsInAccountAndRegionByName(@PathVariable String account, @PathVariable String region, @PathVariable String name) {
    def searchKey = Keys.getLoadBalancerKey(name, account, region)
    Collection<String> identifiers = cacheView.filterIdentifiers(LOAD_BALANCERS.ns, searchKey)
    cacheView.getAll(LOAD_BALANCERS.ns, identifiers).attributes
  }

  public Map<String, CloudFoundryLoadBalancerSummary> getSummaryForLoadBalancers(Collection<String> loadBalancerKeys) {
    Map<String, CloudFoundryLoadBalancerSummary> map = [:]
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
          summary = new CloudFoundryLoadBalancerSummary(name: name)
          map.put name, summary
        }
        def loadBalancer = new CloudFoundryLoadBalancerDetail()
        loadBalancer.account = parts.account
        loadBalancer.region = parts.region
        loadBalancer.name = parts.loadBalancer

        summary.getOrCreateAccount(account).getOrCreateRegion(region).loadBalancers << loadBalancer
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
