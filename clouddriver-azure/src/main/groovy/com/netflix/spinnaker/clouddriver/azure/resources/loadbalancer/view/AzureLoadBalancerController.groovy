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
import com.netflix.spinnaker.clouddriver.azure.common.AzureUtilities
import com.netflix.spinnaker.clouddriver.azure.resources.loadbalancer.model.AzureLoadBalancerDescription
import com.netflix.spinnaker.clouddriver.security.AccountCredentialsProvider
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestMethod
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/azure/loadBalancersL4")
class AzureLoadBalancerController {

  @Autowired
  AccountCredentialsProvider accountCredentialsProvider

  @Autowired
  AzureLoadBalancerProvider azureLoadBalancerProvider

  @RequestMapping(method = RequestMethod.GET)
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

  @RequestMapping(value = "/{account}/{region}/{name:.+}", method = RequestMethod.GET)
  List<Map> getDetailsInAccountAndRegionByName(@PathVariable String account, @PathVariable String region, @PathVariable String name) {
    String appName = AzureUtilities.getAppNameFromAzureResourceName(name)
    AzureLoadBalancerDescription azureLoadBalancerDescription = azureLoadBalancerProvider.getLoadBalancerDescription(account, appName, region, name)

    if (azureLoadBalancerDescription) {
      def lbDetail = [
        name: azureLoadBalancerDescription.loadBalancerName
      ]

      lbDetail.createdTime = azureLoadBalancerDescription.createdTime
      lbDetail.serverGroup = azureLoadBalancerDescription.serverGroup
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

  static class AzureLoadBalancerSummary {
    private Map<String, AzureLoadBalancerAccount> mappedAccounts = [:]
    String name

    AzureLoadBalancerAccount getOrCreateAccount(String name) {
      if (!mappedAccounts.containsKey(name)) {
        mappedAccounts.put(name, new AzureLoadBalancerAccount(name:name))
      }

      mappedAccounts[name]
    }

    @JsonProperty("accounts")
    List<AzureLoadBalancerAccount> getAccounts() {
      mappedAccounts.values() as List
    }
  }

  static class AzureLoadBalancerAccount {
    private Map<String, AzureLoadBalancerAccountRegion> mappedRegions = [:]
    String name

    AzureLoadBalancerAccountRegion getOrCreateRegion(String name) {
      if (!mappedRegions.containsKey(name)) {
        mappedRegions.put(name, new AzureLoadBalancerAccountRegion(name: name, loadBalancers: []))
      }
      mappedRegions[name];
    }

    @JsonProperty("regions")
    List<AzureLoadBalancerAccountRegion> getRegions() {
      mappedRegions.values() as List
    }

  }

  static class AzureLoadBalancerAccountRegion {
    String name
    List<AzureLoadBalancerDetail> loadBalancers
  }

  static class AzureLoadBalancerDetail {
    String account
    String region
    String name
    String type="azure"
  }
}
