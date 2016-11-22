/*
 * Copyright 2016 Pivotal, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.clouddriver.cf.provider.view

import com.fasterxml.jackson.annotation.JsonProperty
import com.netflix.spinnaker.cats.cache.Cache
import com.netflix.spinnaker.cats.cache.CacheData
import com.netflix.spinnaker.cats.cache.RelationshipCacheFilter
import com.netflix.spinnaker.clouddriver.cf.CloudFoundryCloudProvider
import com.netflix.spinnaker.clouddriver.cf.cache.CacheUtils
import com.netflix.spinnaker.clouddriver.cf.cache.Keys
import com.netflix.spinnaker.clouddriver.cf.model.CloudFoundryLoadBalancer
import com.netflix.spinnaker.clouddriver.cf.model.CloudFoundryServerGroup
import com.netflix.spinnaker.clouddriver.cf.provider.CloudFoundryProvider
import com.netflix.spinnaker.clouddriver.cf.provider.ProviderUtils
import com.netflix.spinnaker.clouddriver.model.LoadBalancerProvider
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

import static com.netflix.spinnaker.cats.cache.RelationshipCacheFilter.include
import static com.netflix.spinnaker.clouddriver.cf.cache.Keys.Namespace.*

@Component
class CloudFoundryLoadBalancerProvider implements LoadBalancerProvider<CloudFoundryLoadBalancer> {

  final String cloudProvider = CloudFoundryCloudProvider.ID

  private final Cache cacheView
  private final CloudFoundryProvider cloudFoundryProvider

  @Autowired
  CloudFoundryLoadBalancerProvider(Cache cacheView, CloudFoundryProvider cloudFoundryProvider) {
    this.cacheView = cacheView
    this.cloudFoundryProvider = cloudFoundryProvider
  }

  @Override
  Set<CloudFoundryLoadBalancer> getApplicationLoadBalancers(String applicationName) {
    Map<String, CloudFoundryServerGroup> serverGroups
    Set<String> keys = []

    CacheData application = cacheView.get(APPLICATIONS.ns, Keys.getApplicationKey(applicationName))

    def applicationServerGroups = application ? ProviderUtils.resolveRelationshipData(cacheView, application, SERVER_GROUPS.ns) : []
    applicationServerGroups.each { CacheData serverGroup ->
      keys.addAll(serverGroup.relationships[LOAD_BALANCERS.ns] ?: [])
    }

    def nameMatches = cacheView.filterIdentifiers(LOAD_BALANCERS.ns, '*:' + applicationName)
    nameMatches.addAll(cacheView.filterIdentifiers(LOAD_BALANCERS.ns, '*:' + applicationName + ':*'));

    keys.addAll(nameMatches)

    Collection<CacheData> allLoadBalancers = cacheView.getAll(LOAD_BALANCERS.ns, keys)
    Collection<CacheData> allInstances = ProviderUtils.resolveRelationshipDataForCollection(cacheView, allLoadBalancers, INSTANCES.ns, include(SERVER_GROUPS.ns))

    def loadBalancers = CacheUtils.translateLoadBalancers(allLoadBalancers).values()
    def instances = CacheUtils.translateInstances(cacheView, allInstances).values()

    Collection<CacheData> allServerGroups = ProviderUtils.resolveRelationshipDataForCollection(cacheView, allLoadBalancers, SERVER_GROUPS.ns)

    allServerGroups.each { serverGroupEntry ->
      CacheUtils.translateServerGroup(serverGroupEntry, instances, loadBalancers)
    }

    loadBalancers as Set
  }

  List<CloudFoundryLoadBalancerSummary> list() {
    def searchKey = Keys.getLoadBalancerKey('*', '*', '*')
    Collection<String> identifiers = cacheView.filterIdentifiers(LOAD_BALANCERS.ns, searchKey)
    getSummaryForLoadBalancers(identifiers).values() as List
  }

  CloudFoundryLoadBalancerSummary get(String name) {
    def searchKey = Keys.getLoadBalancerKey(name, '*', '*')
    Collection<String> identifiers = cacheView.filterIdentifiers(LOAD_BALANCERS.ns, searchKey)
    getSummaryForLoadBalancers(identifiers).get(name)
  }

  List<Map> byAccountAndRegionAndName(String account, String region, String name) {
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

  static class CloudFoundryLoadBalancerSummary implements LoadBalancerProvider.Item {
    private Map<String, CloudFoundryLoadBalancerAccount> mappedAccounts = [:]
    String name

    CloudFoundryLoadBalancerAccount getOrCreateAccount(String name) {
      if (!mappedAccounts.containsKey(name)) {
        mappedAccounts.put(name, new CloudFoundryLoadBalancerAccount(name: name))
      }
      mappedAccounts[name]
    }

    @JsonProperty("accounts")
    List<CloudFoundryLoadBalancerAccount> getByAccounts() {
      mappedAccounts.values() as List
    }
  }

  static class CloudFoundryLoadBalancerAccount implements LoadBalancerProvider.ByAccount {
    private Map<String, CloudFoundryLoadBalancerAccountRegion> mappedRegions = [:]
    String name

    CloudFoundryLoadBalancerAccountRegion getOrCreateRegion(String name) {
      if (!mappedRegions.containsKey(name)) {
        mappedRegions.put(name, new CloudFoundryLoadBalancerAccountRegion(name: name, loadBalancers: []))
      }
      mappedRegions[name]
    }

    @JsonProperty("regions")
    List<CloudFoundryLoadBalancerAccountRegion> getByRegions() {
      mappedRegions.values() as List
    }
  }

  static class CloudFoundryLoadBalancerAccountRegion implements LoadBalancerProvider.ByRegion {
    String name
    List<CloudFoundryLoadBalancerDetail> loadBalancers
  }

  static class CloudFoundryLoadBalancerDetail implements LoadBalancerProvider.Details {
    String account
    String region
    String name
    String type = 'cf'
  }
}
