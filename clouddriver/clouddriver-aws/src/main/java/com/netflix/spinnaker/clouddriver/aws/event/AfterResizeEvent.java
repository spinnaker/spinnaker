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
import com.amazonaws.services.ec2.AmazonEC2;
import com.netflix.spinnaker.clouddriver.data.task.Task;
import com.netflix.spinnaker.clouddriver.model.ServerGroup;

public class AfterResizeEvent {
  private final Task task;
  private final AmazonEC2 amazonEC2;
  private final AmazonAutoScaling amazonAutoScaling;
  private final AutoScalingGroup autoScalingGroup;
  private final ServerGroup.Capacity capacity;

  public AfterResizeEvent(
      Task task,
      AmazonEC2 amazonEC2,
      AmazonAutoScaling amazonAutoScaling,
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

  public AmazonEC2 getAmazonEC2() {
    return amazonEC2;
  }

  public AmazonAutoScaling getAmazonAutoScaling() {
    return amazonAutoScaling;
  }

  public AutoScalingGroup getAutoScalingGroup() {
    return autoScalingGroup;
  }

  public ServerGroup.Capacity getCapacity() {
    return capacity;
  }
}
