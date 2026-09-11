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

import com.netflix.spinnaker.clouddriver.data.task.Task;
import com.netflix.spinnaker.clouddriver.model.ServerGroup;
import software.amazon.awssdk.services.autoscaling.AutoScalingClient;
import software.amazon.awssdk.services.autoscaling.model.AutoScalingGroup;
import software.amazon.awssdk.services.ec2.Ec2Client;

public class AfterResizeEvent {
  private final Task task;
  private final Ec2Client amazonEC2;
  private final AutoScalingClient amazonAutoScaling;
  private final AutoScalingGroup autoScalingGroup;
  private final ServerGroup.Capacity capacity;

  public AfterResizeEvent(
      Task task,
      Ec2Client amazonEC2,
      AutoScalingClient amazonAutoScaling,
      AutoScalingGroup autoScalingGroup,
      ServerGroup.Capacity capacity) {
    this.task = task;
    this.amazonEC2 = amazonEC2;
    this.amazonAutoScaling = amazonAutoScaling;
    this.autoScalingGroup = autoScalingGroup;
    this.capacity = capacity;
  }

  public Task getTask() {
    return task;
  }

  public Ec2Client getAmazonEC2() {
    return amazonEC2;
  }

  public AutoScalingClient getAmazonAutoScaling() {
    return amazonAutoScaling;
  }

  public AutoScalingGroup getAutoScalingGroup() {
    return autoScalingGroup;
  }

  public ServerGroup.Capacity getCapacity() {
    return capacity;
  }
}
