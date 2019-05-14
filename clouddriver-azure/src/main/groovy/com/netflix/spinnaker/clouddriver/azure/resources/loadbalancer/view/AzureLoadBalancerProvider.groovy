/*
 * Copyright 2015 The original authors.
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

package com.netflix.spinnaker.clouddriver.azure.resources.loadbalancer.view

import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.cats.cache.Cache
import com.netflix.spinnaker.cats.cache.CacheData
import com.netflix.spinnaker.cats.cache.RelationshipCacheFilter
import com.netflix.spinnaker.clouddriver.azure.AzureCloudProvider
import com.netflix.spinnaker.clouddriver.azure.common.AzureUtilities
import com.netflix.spinnaker.clouddriver.azure.resources.common.cache.Keys
import com.netflix.spinnaker.clouddriver.azure.resources.loadbalancer.model.AzureLoadBalancer
import com.netflix.spinnaker.clouddriver.azure.resources.loadbalancer.model.AzureLoadBalancerDescription
import com.netflix.spinnaker.clouddriver.model.LoadBalancerProvider
import com.netflix.spinnaker.clouddriver.model.LoadBalancerServerGroup
import com.netflix.spinnaker.clouddriver.security.AccountCredentialsProvider
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestMethod
import org.springframework.web.bind.annotation.RestController

@RestController
@Component
class AzureLoadBalancerProvider implements LoadBalancerProvider<AzureLoadBalancer> {

  final String cloudProvider = AzureCloudProvider.ID

  private final AzureCloudProvider azureCloudProvider
  private final Cache cacheView
  final ObjectMapper objectMapper

  @Autowired
  AccountCredentialsProvider accountCredentialsProvider

  @Autowired
  AzureLoadBalancerProvider azureLoadBalancerProvider

  List<AzureLoadBalancerSummary> list() {
    getSummaryForLoadBalancers().values() as List
  }

  private Map<String, AzureLoadBalancerSummary> getSummaryForLoadBalancers() {
    Map<String, AzureLoadBalancerSummary> map = [:]
    def loadBalancers = azureLoadBalancerProvider.getApplicationLoadBalancers('*')

    loadBalancers?.each() { lb ->
      def summary = map.get(lb.name)

      if (!summary) {
        summary = new AzureLoadBalancerSummary(name: lb.name)
        map.put lb.name, summary
      }

      def loadBalancerDetail = new AzureLoadBalancerDetail(account: lb.account, name: lb.name, region: lb.region)

      summary.getOrCreateAccount(lb.account).getOrCreateRegion(lb.region).loadBalancers << loadBalancerDetail
    }
    map
  }

  LoadBalancerProvider.Item get(String name) {
    throw new UnsupportedOperationException("TODO: Implement single getter.")
  }

  List<Map> byAccountAndRegionAndName(String account, String region, String name) {
    String appName = AzureUtilities.getAppNameFromAzureResourceName(name)
    AzureLoadBalancerDescription azureLoadBalancerDescription = azureLoadBalancerProvider.getLoadBalancerDescription(account, appName, region, name)

    if (azureLoadBalancerDescription) {
      def lbDetail = [
        name: azureLoadBalancerDescription.loadBalancerName
      ]

      lbDetail.createdTime = azureLoadBalancerDescription.createdTime
      lbDetail.serverGroup = azureLoadBalancerDescription.serverGroups
      lbDetail.vnet = azureLoadBalancerDescription.vnet ?: "vnet-unassigned"
      lbDetail.subnet = azureLoadBalancerDescription.subnet ?: "subnet-unassigned"
      lbDetail.dnsName = azureLoadBalancerDescription.dnsName ?: "dnsname-unassigned"

      lbDetail.probes = azureLoadBalancerDescription.probes
      lbDetail.securityGroup = azureLoadBalancerDescription.securityGroup
      lbDetail.loadBalancingRules = azureLoadBalancerDescription.loadBalancingRules
      lbDetail.inboundNATRules = azureLoadBalancerDescription.inboundNATRules
      lbDetail.tags = azureLoadBalancerDescription.tags

      return [lbDetail]
    }

    return []
  }

  static class AzureLoadBalancerSummary implements LoadBalancerProvider.Item {
    private Map<String, AzureLoadBalancerAccount> mappedAccounts = [:]
    String name

    AzureLoadBalancerAccount getOrCreateAccount(String name) {
      if (!mappedAccounts.containsKey(name)) {
        mappedAccounts.put(name, new AzureLoadBalancerAccount(name:name))
      }

      mappedAccounts[name]
    }

    @JsonProperty("accounts")
    List<AzureLoadBalancerAccount> getByAccounts() {
      mappedAccounts.values() as List
    }
  }

  static class AzureLoadBalancerAccount implements LoadBalancerProvider.ByAccount {
    private Map<String, AzureLoadBalancerAccountRegion> mappedRegions = [:]
    String name

    AzureLoadBalancerAccountRegion getOrCreateRegion(String name) {
      if (!mappedRegions.containsKey(name)) {
        mappedRegions.put(name, new AzureLoadBalancerAccountRegion(name: name, loadBalancers: []))
      }
      mappedRegions[name];
    }

    @JsonProperty("regions")
    List<AzureLoadBalancerAccountRegion> getByRegions() {
      mappedRegions.values() as List
    }

  }

  static class AzureLoadBalancerAccountRegion implements LoadBalancerProvider.Details {
    String name
    List<AzureLoadBalancerDetail> loadBalancers
  }

  static class AzureLoadBalancerDetail implements LoadBalancerProvider.Details {
    String account
    String region
    String name
    String type="azure"
  }

  @Autowired
  AzureLoadBalancerProvider(AzureCloudProvider azureCloudProvider, Cache cacheView, ObjectMapper objectMapper) {
    this.azureCloudProvider = azureCloudProvider
    this.cacheView = cacheView
    this.objectMapper = objectMapper
  }

  /**
   * Returns all load balancers related to an application based on one of the following criteria:
   *   - the load balancer name follows the Frigga naming conventions for load balancers (i.e., the load balancer name starts with the application name, followed by a hyphen)
   *   - the load balancer is used by a server group in the application
   * @param application the name of the application
   * @return a collection of load balancers with all attributes populated and a minimal amount of data
   *         for each server group: its name, region, and *only* the instances attached to the load balancers described above.
   *         The instances will have a minimal amount of data, as well: name, zone, and health related to any load balancers
   */
//  @Override
  @RequestMapping(value = "/applications/{application}/loadBalancersL4", method = RequestMethod.GET)
  Set<AzureLoadBalancer> getApplicationLoadBalancers(@PathVariable String application) {
    getAllMatchingKeyPattern(Keys.getLoadBalancerKey(azureCloudProvider, '*', '*', application, '*', '*', '*'))
  }

  Set<AzureLoadBalancer> getAllMatchingKeyPattern(String pattern) {
    loadResults(cacheView.filterIdentifiers(Keys.Namespace.AZURE_LOAD_BALANCERS.ns, pattern))
  }

  Set<AzureLoadBalancer> loadResults(Collection<String> identifiers) {
    def transform = this.&fromCacheData
    def data = cacheView.getAll(Keys.Namespace.AZURE_LOAD_BALANCERS.ns, identifiers, RelationshipCacheFilter.none())
    def transformed = data.collect(transform)

    return transformed
  }

  AzureLoadBalancer fromCacheData(CacheData cacheData) {
    AzureLoadBalancerDescription loadBalancerDescription = objectMapper.convertValue(cacheData.attributes['loadbalancer'], AzureLoadBalancerDescription)
    def parts = Keys.parse(azureCloudProvider, cacheData.id)

    def loadBalancer = new AzureLoadBalancer(
      account: parts.account ?: "none",
      name: loadBalancerDescription.loadBalancerName,
      region: loadBalancerDescription.region,
      vnet: loadBalancerDescription.vnet ?: "vnet-unassigned",
      subnet: loadBalancerDescription.subnet ?: "subnet-unassigned",
      loadBalancerType: AzureLoadBalancer.AzureLoadBalancerType.AZURE_LOAD_BALANCER
    )

    loadBalancerDescription.serverGroups?.each { serverGroup ->
      // TODO: add proper check for enable/disable server groups
      loadBalancer.serverGroups.add(new LoadBalancerServerGroup (
        name: serverGroup,
        isDisabled: false,
        detachedInstances: [],
        instances: [],
        cloudProvider: AzureCloudProvider.ID
      ))
    }

    loadBalancer
  }

  AzureLoadBalancerDescription getLoadBalancerDescription(String account, String appName, String region, String loadBalancerName) {
    def pattern = Keys.getLoadBalancerKey(azureCloudProvider, loadBalancerName, '*', appName, '*', region, account)
    def identifiers = cacheView.filterIdentifiers(Keys.Namespace.AZURE_LOAD_BALANCERS.ns, pattern)
    def data = cacheView.getAll(Keys.Namespace.AZURE_LOAD_BALANCERS.ns, identifiers, RelationshipCacheFilter.none())
    CacheData cacheData = data? data.first() : null

    cacheData? objectMapper.convertValue(cacheData.attributes['loadbalancer'], AzureLoadBalancerDescription) : null
  }

}
