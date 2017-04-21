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
import com.amazonaws.services.elasticloadbalancingv2.model.DescribeLoadBalancersRequest
import com.amazonaws.services.elasticloadbalancingv2.model.DescribeTargetGroupsRequest
import com.amazonaws.services.elasticloadbalancingv2.model.LoadBalancerNotFoundException
import com.netflix.spinnaker.clouddriver.aws.services.RegionScopedProviderFactory

class LoadBalancerLookupHelper {

  static class LoadBalancerLookupResult {
    Set<String> classicLoadBalancers = []
    Set<String> targetGroupArns = []
    Set<String> unknownLoadBalancers = []
  }

  public LoadBalancerLookupHelper() {
  }

  LoadBalancerLookupResult getLoadBalancersFromAsg(AutoScalingGroup asg) {
    def result = new LoadBalancerLookupResult()
    result.classicLoadBalancers.addAll(asg.loadBalancerNames ?: [])
    result.targetGroupArns.addAll(asg.targetGroupARNs ?: [])
    return result
  }

  LoadBalancerLookupResult getLoadBalancersByName(RegionScopedProviderFactory.RegionScopedProvider rsp, Collection<String> loadBalancerNames) {
    def result = new LoadBalancerLookupResult()
    Set<String> allLoadBalancers = new HashSet<>(loadBalancerNames ?: [])
    if (!allLoadBalancers) {
      return result
    }
    def lbv2 = rsp.getAmazonElasticLoadBalancingV2()
    def lbv1 = rsp.getAmazonElasticLoadBalancing()
    Set<String> v2LoadBalancers = []
    for (String lbName : allLoadBalancers) {
      //at the moment, '--' is not allowed in lbv2 load balancer names, and asking for it throws a ValidationError not a LoadBalancerNotFoundException
      if (!lbName.contains("--")) {
        try {
          def lb = lbv2.describeLoadBalancers(new DescribeLoadBalancersRequest().withNames(lbName)).loadBalancers.first()
          v2LoadBalancers.add(lbName)
          result.targetGroupArns.addAll(lbv2.describeTargetGroups(new DescribeTargetGroupsRequest().withLoadBalancerArn(lb.loadBalancerArn)).targetGroups*.targetGroupArn)
        } catch (LoadBalancerNotFoundException lbnfe) {
          //ignore
        }
      }

      try {
        lbv1.describeLoadBalancers(new com.amazonaws.services.elasticloadbalancing.model.DescribeLoadBalancersRequest().withLoadBalancerNames(lbName))
        result.classicLoadBalancers.add(lbName)
      } catch (com.amazonaws.services.elasticloadbalancing.model.LoadBalancerNotFoundException lbnfe) {
        //ignore
      }
    }
    allLoadBalancers.removeAll(result.classicLoadBalancers)
    allLoadBalancers.removeAll(v2LoadBalancers)

    result.unknownLoadBalancers.addAll(allLoadBalancers)

    return result
  }
}
