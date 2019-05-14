/*
 * Copyright 2016 The original authors.
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

package com.netflix.spinnaker.clouddriver.azure.resources.appgateway.view

import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.cats.cache.Cache
import com.netflix.spinnaker.cats.cache.CacheData
import com.netflix.spinnaker.cats.cache.RelationshipCacheFilter
import com.netflix.spinnaker.clouddriver.azure.AzureCloudProvider
import com.netflix.spinnaker.clouddriver.azure.common.AzureUtilities
import com.netflix.spinnaker.clouddriver.azure.resources.appgateway.model.AzureAppGatewayDescription
import com.netflix.spinnaker.clouddriver.azure.resources.common.cache.Keys
import com.netflix.spinnaker.clouddriver.azure.resources.loadbalancer.model.AzureLoadBalancer
import com.netflix.spinnaker.clouddriver.model.LoadBalancerProvider
import com.netflix.spinnaker.clouddriver.model.LoadBalancerServerGroup
import com.netflix.spinnaker.clouddriver.security.AccountCredentialsProvider
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

@Component
class AzureAppGatewayProvider implements LoadBalancerProvider<AzureLoadBalancer> {

  final String cloudProvider = AzureCloudProvider.ID

  private final AzureCloudProvider azureCloudProvider
  private final Cache cacheView
  final ObjectMapper objectMapper

  @Autowired
  AccountCredentialsProvider accountCredentialsProvider

  @Autowired
  AzureAppGatewayProvider(AzureCloudProvider azureCloudProvider, Cache cacheView, ObjectMapper objectMapper) {
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
  @Override
  Set<AzureLoadBalancer> getApplicationLoadBalancers(String application) {
    getAllMatchingKeyPattern(Keys.getAppGatewayKey(azureCloudProvider, application, '*', '*', '*'))
  }

  Set<AzureLoadBalancer> getAllMatchingKeyPattern(String pattern) {
    loadResults(cacheView.filterIdentifiers(Keys.Namespace.AZURE_APP_GATEWAYS.ns, pattern))
  }

  Set<AzureLoadBalancer> loadResults(Collection<String> identifiers) {
    def transform = this.&fromCacheData
    def data = cacheView.getAll(Keys.Namespace.AZURE_APP_GATEWAYS.ns, identifiers, RelationshipCacheFilter.none())
    def transformed = data.collect(transform)

    return transformed
  }

  AzureLoadBalancer fromCacheData(CacheData cacheData) {
    AzureAppGatewayDescription description = objectMapper.convertValue(cacheData.attributes['appgateway'], AzureAppGatewayDescription)
    def parts = Keys.parse(azureCloudProvider, cacheData.id)

    def loadBalancer = new AzureLoadBalancer(
      account: parts.account ?: "none",
      name: description.loadBalancerName,
      region: description.region,
      vnet: description.vnet ?: "vnet-unassigned",
      subnet: description.subnet ?: "subnet-unassigned",
      cluster: description.cluster ?: "unassigned",
      loadBalancerType: AzureLoadBalancer.AzureLoadBalancerType.AZURE_APPLICATION_GATEWAY
    )
    description.serverGroups?.each { serverGroup ->
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

  AzureAppGatewayDescription getAppGatewayDescription(String account, String appName, String region, String appGatewayName) {
    def data = cacheView.getAll(
      Keys.Namespace.AZURE_APP_GATEWAYS.ns,
      cacheView.filterIdentifiers(Keys.Namespace.AZURE_APP_GATEWAYS.ns,
        Keys.getAppGatewayKey(azureCloudProvider, appName, appGatewayName, region, account)),
      RelationshipCacheFilter.none()
    )
//    CacheData cacheData = cacheView.get(Keys.Namespace.AZURE_APP_GATEWAYS.ns, Keys.getAppGatewayKey(azureCloudProvider, appName, appGatewayName, region, account))
    CacheData cacheData = data? data.first() : null

    cacheData? objectMapper.convertValue(cacheData.attributes['appgateway'], AzureAppGatewayDescription) : null
  }

  List<AzureAppGatewaySummary> list() {
    getSummaryForAppGateways().values() as List
  }

  private Map<String, AzureAppGatewaySummary> getSummaryForAppGateways() {
    Map<String, AzureAppGatewaySummary> map = [:]
    def loadBalancers = getApplicationLoadBalancers('*')

    loadBalancers?.each() { lb ->
      def summary = map.get(lb.name)

      if (!summary) {
        summary = new AzureAppGatewaySummary(name: lb.name)
        map.put lb.name, summary
      }

      def loadBalancerDetail = new AzureAppGatewayAccountRegionDetail(account: lb.account, name: lb.name, region: lb.region)

      summary.getOrCreateAccount(lb.account).getOrCreateRegion(lb.region).loadBalancers << loadBalancerDetail
    }
    map
  }

  @Override
  LoadBalancerProvider.Item get(String name) {
    throw new UnsupportedOperationException("TODO: Implement single getter.")
  }

  List<Map> byAccountAndRegionAndName(String account, String region, String name) {
    String appName = AzureUtilities.getAppNameFromAzureResourceName(name)
    AzureAppGatewayDescription description = getAppGatewayDescription(account, appName, region, name)

    if (description) {
      def lbDetail = [
          name: description.loadBalancerName
      ]

      lbDetail.account = account
      lbDetail.region = region
      lbDetail.application = appName
      lbDetail.stack = description.stack
      lbDetail.detail = description.detail
      lbDetail.createdTime = description.createdTime
      lbDetail.cluster = description.cluster ?: "unassigned"
      lbDetail.serverGroups = description.serverGroups?.join(" ") ?: "unassigned"
      lbDetail.securityGroup = description.securityGroup ?: "unassigned"
      lbDetail.vnet = description.vnet ?: "vnet-unassigned"
      lbDetail.subnet = description.subnet ?: "subnet-unassigned"
      lbDetail.dnsName = description.dnsName ?: "dnsname-unassigned"

      lbDetail.probes = description.probes
      lbDetail.loadBalancingRules = description.loadBalancingRules
      lbDetail.tags = description.tags

      lbDetail.sku = description.sku
      lbDetail.tier = description.tier
      lbDetail.capacity = description.capacity

      return [lbDetail]
    }

    return []
  }

  static class AzureAppGatewaySummary implements LoadBalancerProvider.Item {
    private Map<String, AzureAppGatewayAccount> mappedAccounts = [:]
    String name

    AzureAppGatewayAccount getOrCreateAccount(String name) {
      if (!mappedAccounts[name]) {
        mappedAccounts[name] = new AzureAppGatewayAccount(name:name)
      }
      mappedAccounts[name]
    }

    @JsonProperty("accounts")
    List<AzureAppGatewayAccount> getByAccounts() {
      mappedAccounts.values() as List
    }
  }

  static class AzureAppGatewayAccount implements LoadBalancerProvider.ByAccount {
    private Map<String, AzureAppGatewayAccountRegion> mappedRegions = [:]
    String name

    AzureAppGatewayAccountRegion getOrCreateRegion(String name) {
      if (!mappedRegions[name]) {
        mappedRegions[name] =  new AzureAppGatewayAccountRegion(name: name, loadBalancers: [])
      }
      mappedRegions[name];
    }

    @JsonProperty("regions")
    List<AzureAppGatewayAccountRegion> getByRegions() {
      mappedRegions.values() as List
    }
  }

  static class AzureAppGatewayAccountRegion implements LoadBalancerProvider.Details {
    String name
    List<AzureAppGatewayAccountRegionDetail> loadBalancers = []
  }

  static class AzureAppGatewayAccountRegionDetail implements LoadBalancerProvider.Details {
    String type="azure"
    String account
    String region
    String name
  }
}
