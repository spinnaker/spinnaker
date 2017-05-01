/*
 * Copyright 2016 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.clouddriver.aws.deploy.ops.loadbalancer

import com.amazonaws.services.autoscaling.model.AutoScalingGroup
import com.amazonaws.services.elasticloadbalancing.model.DescribeLoadBalancersRequest
import com.amazonaws.services.elasticloadbalancing.model.LoadBalancerNotFoundException
import com.netflix.spinnaker.clouddriver.aws.services.RegionScopedProviderFactory

class LoadBalancerLookupHelper {

  static class LoadBalancerLookupResult {
    Set<String> classicLoadBalancers = []
    Set<String> unknownLoadBalancers = []
  }

  public LoadBalancerLookupHelper() {
  }

  LoadBalancerLookupResult getLoadBalancersFromAsg(AutoScalingGroup asg) {
    def result = new LoadBalancerLookupResult()
    result.classicLoadBalancers.addAll(asg.loadBalancerNames ?: [])
    return result
  }

  LoadBalancerLookupResult getLoadBalancersByName(RegionScopedProviderFactory.RegionScopedProvider rsp, Collection<String> loadBalancerNames) {
    def result = new LoadBalancerLookupResult()
    Set<String> allLoadBalancers = new HashSet<>(loadBalancerNames ?: [])
    if (!allLoadBalancers) {
      return result
    }
    def elbClassic = rsp.getAmazonElasticLoadBalancing()
    for (String lbName : allLoadBalancers) {
      try {
        def foo = elbClassic.describeLoadBalancers(new DescribeLoadBalancersRequest().withLoadBalancerNames(lbName))
        result.classicLoadBalancers.add(lbName)
      } catch (LoadBalancerNotFoundException loadBalancerNotFoundException) {
        // ignore
      }
    }
    allLoadBalancers.removeAll(result.classicLoadBalancers)

    result.unknownLoadBalancers.addAll(allLoadBalancers)

    return result
  }
}
