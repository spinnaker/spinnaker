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

package com.netflix.spinnaker.gate.services

import com.netflix.spinnaker.gate.services.commands.HystrixFactory
import com.netflix.spinnaker.gate.services.internal.ClouddriverService
import groovy.transform.CompileStatic
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

@CompileStatic
@Component
class LoadBalancerService {
  private static final String GROUP = "loadBalancers"

  @Autowired
  ClouddriverService clouddriverService

  List<Map> getAll(String provider = "aws", String selectorKey) {
    HystrixFactory.newListCommand(GROUP, "getAllLoadBalancersForProvider-$provider") {
      clouddriverService.getLoadBalancers(provider)
    } execute()
  }

  Map get(String name, String selectorKey, String provider = "aws") {
    HystrixFactory.newMapCommand(GROUP, "getLoadBalancer-$provider") {
      clouddriverService.getLoadBalancer(provider, name)
    } execute()
  }

  List<Map> getDetailsForAccountAndRegion(String account, String region, String name, String selectorKey, String provider = "aws") {
    HystrixFactory.newListCommand(GROUP, "getLoadBalancerDetails-$provider") {
      clouddriverService.getLoadBalancerDetails(provider, account, region, name)
    } execute()
  }

  List getClusterLoadBalancers(String appName, String account, String provider, String clusterName, String selectorKey) {
    HystrixFactory.newListCommand(GROUP,
        "getClusterLoadBalancers-$provider") {
      clouddriverService.getClusterLoadBalancers(appName, account, clusterName, provider)
    } execute()
  }

  List getApplicationLoadBalancers(String appName, String selectorKey) {
    HystrixFactory.newListCommand(GROUP,
      "getApplicationLoadBalancers") {
      clouddriverService.getApplicationLoadBalancers(appName)
    } execute()
  }
}
