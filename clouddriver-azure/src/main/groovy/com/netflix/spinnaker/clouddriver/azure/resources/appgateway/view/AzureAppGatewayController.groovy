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
import com.netflix.spinnaker.clouddriver.azure.common.AzureUtilities
import com.netflix.spinnaker.clouddriver.azure.resources.appgateway.model.AzureAppGatewayDescription
import com.netflix.spinnaker.clouddriver.azure.resources.loadbalancer.model.AzureLoadBalancerDescription
import com.netflix.spinnaker.clouddriver.azure.resources.loadbalancer.view.AzureLoadBalancerProvider
import com.netflix.spinnaker.clouddriver.security.AccountCredentialsProvider
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestMethod
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/azure/appGateways")
class AzureAppGatewayController {

  @Autowired
  AccountCredentialsProvider accountCredentialsProvider

  @Autowired
  AzureAppGatewayProvider azureAppGatewayProvider

  @RequestMapping(method = RequestMethod.GET)
  List<AzureAppGatewaySummary> list() {
    getSummaryForAppGateways().values() as List
  }

  private Map<String, AzureAppGatewaySummary> getSummaryForAppGateways() {
    Map<String, AzureAppGatewaySummary> map = [:]
    def loadBalancers = azureAppGatewayProvider.getApplicationLoadBalancers('*')

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

  @RequestMapping(value = "/{account}/{region}/{name:.+}", method = RequestMethod.GET)
  List<Map> getDetailsInAccountAndRegionByName(@PathVariable String account, @PathVariable String region, @PathVariable String name) {
    String appName = AzureUtilities.getAppNameFromAzureResourceName(name)
    AzureAppGatewayDescription description = azureAppGatewayProvider.getAppGatewayDescription(account, appName, region, name)

    if (description) {
      def lbDetail = [
        name: description.loadBalancerName
      ]

      lbDetail.createdTime = description.createdTime
      lbDetail.cluster = description.cluster ?: "unassigned"
      lbDetail.serverGroups = description.serverGroups?.join(" ") ?: "unassigned"
      lbDetail.securityGroup = description.securityGroup ?: "unassigned"
      lbDetail.vnet = description.vnet ?: "vnet-unassigned"
      lbDetail.subnet = description.subnet ?: "subnet-unassigned"
      lbDetail.dnsName = description.dnsName ?: "dnsname-unassigned"

      lbDetail.probes = description.probes
      lbDetail.loadBalancingRules = description.rules
      lbDetail.tags = description.tags

      lbDetail.sku = description.sku
      lbDetail.tier = description.tier
      lbDetail.capacity = description.capacity

      return [lbDetail]
    }

    return []
  }

  static class AzureAppGatewaySummary {
    private Map<String, AzureAppGatewayAccount> mappedAccounts = [:]
    String name

    AzureAppGatewayAccount getOrCreateAccount(String name) {
      if (!mappedAccounts[name]) {
        mappedAccounts[name] = new AzureAppGatewayAccount(name:name)
      }

      mappedAccounts[name]
    }
  }

  static class AzureAppGatewayAccount {
    private Map<String, AzureAppGatewayAccountRegion> mappedRegions = [:]
    String name

    AzureAppGatewayAccountRegion getOrCreateRegion(String name) {
      if (!mappedRegions[name]) {
        mappedRegions[name] =  new AzureAppGatewayAccountRegion(name: name, loadBalancers: [])
      }

      mappedRegions[name];
    }

    @JsonProperty("regions")
    List<AzureAppGatewayAccountRegion> getRegions() {
      mappedRegions.values() as List
    }

  }

  static class AzureAppGatewayAccountRegion {
    String name
    List<AzureAppGatewayAccountRegionDetail> loadBalancers = []
  }

  static class AzureAppGatewayAccountRegionDetail {
    String type="azure"
    String account
    String region
    String name
  }
}
