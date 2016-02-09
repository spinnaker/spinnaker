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

import com.netflix.spinnaker.clouddriver.azure.common.AzureResourceRetriever
import com.netflix.spinnaker.clouddriver.azure.resources.loadbalancer.model.AzureLoadBalancer
import com.netflix.spinnaker.clouddriver.azure.resources.loadbalancer.model.AzureLoadBalancerDescription
import com.netflix.spinnaker.clouddriver.model.LoadBalancerProvider
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

@Component
class AzureLoadBalancerProvider implements LoadBalancerProvider<AzureLoadBalancer> {

  @Autowired
  AzureResourceRetriever azureResourceRetriever

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
    List<AzureLoadBalancer> applicationLoadBalancers = new ArrayList<AzureLoadBalancer>()

    azureResourceRetriever.applicationLoadBalancerMap.each() { account, appMap ->
      if (appMap.containsKey(application)) {
        for (AzureLoadBalancerDescription lb : appMap[application]) {
          def azureLB = new AzureLoadBalancer(account: account, name: lb.loadBalancerName, region: lb.region)
          applicationLoadBalancers.add(azureLB)
        }
      }
    }
    applicationLoadBalancers
  }

}
