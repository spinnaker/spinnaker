/*
 * Copyright 2018 Netflix, Inc.
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

package com.netflix.spinnaker.clouddriver.aws.event;

import com.amazonaws.services.autoscaling.AmazonAutoScaling;
import com.amazonaws.services.autoscaling.model.AutoScalingGroup;
import com.amazonaws.services.autoscaling.model.DescribeLifecycleHooksRequest;
import com.amazonaws.services.autoscaling.model.DescribeLifecycleHooksResult;
import com.amazonaws.services.autoscaling.model.LifecycleHook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.stream.Collectors;

public class DefaultAfterResizeEventHandler implements AfterResizeEventHandler {
  private final Logger log = LoggerFactory.getLogger(getClass());

  /**
   * There is an opportunity to expedite a resize to zero by explicitly terminating instances
   * (server group _must not_ be attached to a load balancer nor have any life cycle hooks)
   */
  @Override
  public void handle(AfterResizeEvent event) {
    AutoScalingGroup autoScalingGroup = event.getAutoScalingGroup();

    if (event.getCapacity() == null || event.getCapacity().getDesired() == null) {
      return;
    }

    if (event.getCapacity().getDesired() > 0) {
      return;
    }

    if (!autoScalingGroup.getLoadBalancerNames().isEmpty() || !autoScalingGroup.getTargetGroupARNs().isEmpty()) {
      event.getTask().updateStatus(
        PHASE,
        "Skipping explicit instance termination, server group is attached to one or more load balancers"
      );
      return;
    }

    try {
      List<LifecycleHook> existingLifecycleHooks = fetchTerminatingLifecycleHooks(
        event.getAmazonAutoScaling(),
        autoScalingGroup.getAutoScalingGroupName()
      );
      if (!existingLifecycleHooks.isEmpty()) {
        event.getTask().updateStatus(
          PHASE,
          "Skipping explicit instance termination, server group has one or more lifecycle hooks"
        );
        return;
      }
    } catch (Exception e) {
      log.error(
        "Unable to fetch lifecycle hooks (serverGroupName: {}, arn: {})",
        autoScalingGroup.getAutoScalingGroupName(),
        autoScalingGroup.getAutoScalingGroupARN(),
        e
      );

      event.getTask().updateStatus(
        PHASE,
        String.format(
          "Skipping explicit instance termination, unable to fetch lifecycle hooks (reason: '%s')",
          e.getMessage()
        )
      );
      return;
    }

    terminateInstancesInAutoScalingGroup(
      event.getTask(), event.getAmazonEC2(), event.getAutoScalingGroup()
    );
  }

  private static List<LifecycleHook> fetchTerminatingLifecycleHooks(AmazonAutoScaling amazonAutoScaling,
                                                                    String serverGroupName) {
    DescribeLifecycleHooksRequest request = new DescribeLifecycleHooksRequest()
      .withAutoScalingGroupName(serverGroupName);

    return amazonAutoScaling.describeLifecycleHooks(request)
      .getLifecycleHooks()
      .stream()
      .filter(h -> "autoscaling:EC2_INSTANCE_TERMINATING".equalsIgnoreCase(h.getLifecycleTransition()))
      .collect(Collectors.toList());
  }
}
