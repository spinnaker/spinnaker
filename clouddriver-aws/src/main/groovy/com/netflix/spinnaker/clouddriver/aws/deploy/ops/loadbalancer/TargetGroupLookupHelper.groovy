/*
 * Copyright 2017 Netflix, Inc.
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
import com.amazonaws.services.elasticloadbalancingv2.model.DescribeTargetGroupsRequest
import com.amazonaws.services.elasticloadbalancingv2.model.LoadBalancerNotFoundException
import com.amazonaws.services.elasticloadbalancingv2.model.TargetGroupNotFoundException
import com.netflix.spinnaker.clouddriver.aws.services.RegionScopedProviderFactory

class TargetGroupLookupHelper {

  static class TargetGroupLookupResult {
    Set<String> targetGroupARNs = []
    Set<String> unknownTargetGroups = []
  }

  public TargetGroupLookupHelper() {
  }

  TargetGroupLookupResult getTargetGroupsFromAsg(AutoScalingGroup asg) {
    def result = new TargetGroupLookupResult()
    result.targetGroupARNs.addAll(asg.targetGroupARNs ?: [])
    return result
  }

  TargetGroupLookupResult getTargetGroupsByName(RegionScopedProviderFactory.RegionScopedProvider rsp, Collection<String> targetGroupNames) {
    def result = new TargetGroupLookupResult()
    Set<String> allTargetGroups = new HashSet<>(targetGroupNames ?: [])
    if (!allTargetGroups) {
      return result
    }
    def lbv2 = rsp.getAmazonElasticLoadBalancingV2()
    Set<String> targetGroups = []
    for (String targetGroupName : allTargetGroups) {
      // at the moment, '--' is not allowed in lbv2 load balancer names, and asking for it throws a ValidationError not a LoadBalancerNotFoundException
      if (!targetGroupName.contains("--")) {
        try {
          def targetGroup = lbv2.describeTargetGroups(new DescribeTargetGroupsRequest().withNames(targetGroupName)).targetGroups.first()
          targetGroups.add(targetGroupName)
          result.targetGroupARNs.add(targetGroup.targetGroupArn)
        } catch (LoadBalancerNotFoundException loadBalancerNotFoundException) {
          // ignore
        } catch (TargetGroupNotFoundException targetGroupNotFoundException) {
          // ignore
        }
      }
    }
    allTargetGroups.removeAll(targetGroups)

    result.unknownTargetGroups.addAll(allTargetGroups)

    return result
  }
}
