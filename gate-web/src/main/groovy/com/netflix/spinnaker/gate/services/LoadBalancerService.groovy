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
import groovy.transform.CompileStatic
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

@CompileStatic
@Component
class LoadBalancerService {
  private static final String GROUP = "loadBalancers"

  @Autowired
  OortService oortService

  List<Map> getAll(String provider = "aws") {
    HystrixFactory.newListCommand(GROUP, "loadbalancers-${provider}-all".toString(), true) {
      try {
        oortService.getLoadBalancers(provider)
      } catch (Exception e) {
        throw e
      }
    } execute()
  }

  Map get(String name, String provider = "aws") {
    HystrixFactory.newMapCommand(GROUP, "loadBalancers-${provider}-${name}".toString(), true) {
      try {
        oortService.getLoadBalancer(provider, name)
      } catch (Exception e) {
        throw e
      }
    } execute()
  }

  List getClusterLoadBalancers(String appName, String account, String provider, String clusterName) {
    HystrixFactory.newListCommand(GROUP,
        "clusterloadBalancers-${provider}-${appName}-${account}-${clusterName}".toString(), true) {
      oortService.getClusterLoadBalancers(appName, account, clusterName, provider)
    } execute()
  }
}
