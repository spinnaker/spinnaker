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

import com.google.common.collect.Lists;
import com.netflix.spinnaker.clouddriver.data.task.Task;
import java.util.List;
import java.util.stream.Collectors;
import software.amazon.awssdk.services.autoscaling.model.AutoScalingGroup;
import software.amazon.awssdk.services.autoscaling.model.Instance;
import software.amazon.awssdk.services.ec2.Ec2Client;
import software.amazon.awssdk.services.ec2.model.TerminateInstancesRequest;

public interface AfterResizeEventHandler {
  int MAX_SIMULTANEOUS_TERMINATIONS = 100;
  String PHASE = "RESIZE";

  void handle(AfterResizeEvent event);

  default void terminateInstancesInAutoScalingGroup(
      Task task, Ec2Client amazonEC2, AutoScalingGroup autoScalingGroup) {
    String serverGroupName = autoScalingGroup.autoScalingGroupName();

    List<String> instanceIds =
        autoScalingGroup.instances().stream()
            .map(Instance::instanceId)
            .collect(Collectors.toList());

    int terminatedCount = 0;
    for (List<String> partition : Lists.partition(instanceIds, MAX_SIMULTANEOUS_TERMINATIONS)) {
      try {
        terminatedCount += partition.size();
        task.updateStatus(
            PHASE,
            String.format(
                "Terminating %d of %d instances in %s",
                terminatedCount, instanceIds.size(), serverGroupName));
        amazonEC2.terminateInstances(
            TerminateInstancesRequest.builder().instanceIds(partition).build());
      } catch (Exception e) {
        task.updateStatus(
            PHASE, String.format("Unable to terminate instances, reason: '%s'", e.getMessage()));
      }
    }
  }
}
