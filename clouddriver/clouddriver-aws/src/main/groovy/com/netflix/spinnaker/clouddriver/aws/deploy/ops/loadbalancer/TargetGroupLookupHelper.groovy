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

import software.amazon.awssdk.services.autoscaling.model.AutoScalingGroup
import software.amazon.awssdk.services.elasticloadbalancingv2.model.DescribeTargetGroupsRequest
import software.amazon.awssdk.services.elasticloadbalancingv2.model.LoadBalancerNotFoundException
import software.amazon.awssdk.services.elasticloadbalancingv2.model.TargetGroupNotFoundException
import com.netflix.spinnaker.clouddriver.aws.services.RegionScopedProviderFactory

class TargetGroupLookupHelper {

  static class TargetGroupLookupResult {
    Set<String> targetGroupARNs = []
    Set<String> unknownTargetGroups = []
  }

  TargetGroupLookupHelper() {
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
    def lbv2 = rsp.getElasticLoadBalancingV2Client()
    Set<String> targetGroups = []
    for (String targetGroupName : allTargetGroups) {
      try {
        def targetGroup = lbv2.describeTargetGroups(DescribeTargetGroupsRequest.builder().names(targetGroupName).build()).targetGroups().first()
        targetGroups.add(targetGroupName)
        result.targetGroupARNs.add(targetGroup.targetGroupArn())
      } catch (LoadBalancerNotFoundException ignore) {
        // ignore
      } catch (TargetGroupNotFoundException ignore) {
        // ignore
      }
    }

    allTargetGroups.removeAll(targetGroups)

    result.unknownTargetGroups.addAll(allTargetGroups)

    return result
  }
}
