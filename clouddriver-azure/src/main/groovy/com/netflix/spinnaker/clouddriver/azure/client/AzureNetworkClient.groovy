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

package com.netflix.spinnaker.clouddriver.azure.client

import com.microsoft.azure.management.network.NetworkResourceProviderClient
import com.microsoft.azure.management.network.NetworkResourceProviderService
import com.microsoft.azure.management.network.models.LoadBalancer
import com.microsoft.azure.utility.NetworkHelper
import com.microsoft.windowsazure.core.OperationResponse
import com.netflix.spinnaker.clouddriver.azure.security.AzureCredentials
import groovy.transform.CompileStatic

@CompileStatic
class AzureNetworkClient extends AzureBaseClient {
  AzureNetworkClient(String subscriptionId) {
    super(subscriptionId)
  }

  Collection<LoadBalancer> getLoadBalancersAll(AzureCredentials creds) {
    this.getNetworkResourceProviderClient(creds).getLoadBalancersOperations().listAll().getLoadBalancers()
  }

  Collection<LoadBalancer> getLoadBalancersForResourceGroup(AzureCredentials creds, String resourceGroupName) {
    this.getNetworkResourceProviderClient(creds).getLoadBalancersOperations().list(resourceGroupName).getLoadBalancers()
  }

  LoadBalancer getLoadBalancer(AzureCredentials creds, String loadBalancerName) {
    findLoadBalancer(getLoadBalancersAll(creds), loadBalancerName)
  }

  LoadBalancer getLoadBalancerInResourceGroup(AzureCredentials creds, String resourceGroupName, String loadBalanacerName) {
    findLoadBalancer(getLoadBalancersForResourceGroup(creds, resourceGroupName), loadBalanacerName)
  }

  String getLoadBalancerHealthState(AzureCredentials creds, String loadBalancerName) {
    getLoadBalancer(creds, loadBalancerName).getProvisioningState();
  }

  protected NetworkResourceProviderClient getNetworkResourceProviderClient(AzureCredentials creds) {
    NetworkResourceProviderService.create(this.buildConfiguration(creds))
  }

  private static LoadBalancer findLoadBalancer(Collection<LoadBalancer> loadBalancers, String loadBalancerName) {
    loadBalancers.find { it.name == loadBalancerName }
  }

  OperationResponse deleteLoadBalancer(AzureCredentials creds, String appName, String loadBalancerName, String region) {
    String resourceGroupName = appName // TODO region will be capture as part of the Azure resource group name

    this.getNetworkResourceProviderClient(creds).getLoadBalancersOperations().delete(resourceGroupName, loadBalancerName)
  }

}
