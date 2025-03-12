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

import com.amazonaws.AmazonServiceException
import com.amazonaws.services.autoscaling.model.AutoScalingGroup
import com.amazonaws.services.elasticloadbalancingv2.model.DescribeTargetGroupsRequest
import com.amazonaws.services.elasticloadbalancingv2.model.LoadBalancerNotFoundException
import com.amazonaws.services.elasticloadbalancingv2.model.TargetGroupNotFoundException
import com.netflix.spinnaker.clouddriver.aws.services.RegionScopedProviderFactory

import java.lang.reflect.InvocationTargetException
import java.lang.reflect.UndeclaredThrowableException

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
    def lbv2 = rsp.getAmazonElasticLoadBalancingV2(false)
    Set<String> targetGroups = []
    for (String targetGroupName : allTargetGroups) {
      try {
        def targetGroup = lbv2.describeTargetGroups(new DescribeTargetGroupsRequest().withNames(targetGroupName)).targetGroups.first()
        targetGroups.add(targetGroupName)
        result.targetGroupARNs.add(targetGroup.targetGroupArn)
      } catch (LoadBalancerNotFoundException ignore) {
        // ignore
      } catch (TargetGroupNotFoundException ignore) {
        // ignore
      } catch (UndeclaredThrowableException e) {
        // There are edda calls hidden behind an .invoke from Aws SDK. Exceptions from
        // those methods show up wrapped in UndeclaredThrowable and/or InvocationTarget
        // If it is a legitimate failure, makes sense to throw the actual error
        boolean rethrow = true
        Throwable toRethrow = e.undeclaredThrowable

        if (e.undeclaredThrowable instanceof InvocationTargetException) {
          InvocationTargetException ite = (InvocationTargetException)e.undeclaredThrowable

          toRethrow = ite.targetException
          if (ite.targetException instanceof AmazonServiceException) {
            AmazonServiceException ase = (AmazonServiceException)ite.targetException

            if (ase.statusCode == 404) {
              rethrow = false
            }
          }
        }

        if (rethrow) {
          throw toRethrow
        }
      }
    }

    allTargetGroups.removeAll(targetGroups)

    result.unknownTargetGroups.addAll(allTargetGroups)

    return result
  }
}
