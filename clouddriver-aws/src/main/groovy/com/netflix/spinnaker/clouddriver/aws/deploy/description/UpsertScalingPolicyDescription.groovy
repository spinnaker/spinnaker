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

package com.netflix.spinnaker.clouddriver.aws.deploy.description

import com.amazonaws.services.autoscaling.model.StepAdjustment
import com.amazonaws.services.autoscaling.model.TargetTrackingConfiguration
import com.netflix.spinnaker.clouddriver.security.resources.ServerGroupsNameable

class UpsertScalingPolicyDescription extends AbstractAmazonCredentialsDescription implements ServerGroupsNameable {

  // required
  String region
  String serverGroupName
  AdjustmentType adjustmentType = AdjustmentType.ChangeInCapacity

  // optional
  String name
  Integer minAdjustmentMagnitude

  Simple simple
  Step step
  TargetTrackingConfiguration targetTrackingConfiguration

  // required for target tracking
  Integer estimatedInstanceWarmup

  UpsertAlarmDescription alarm

  @Override
  Collection<String> getServerGroupNames() {
    return [serverGroupName]
  }

  static class Simple {
    Integer cooldown = 600
    Integer	scalingAdjustment
  }

  static class Step {
    Collection<StepAdjustment> stepAdjustments
    Integer estimatedInstanceWarmup
    MetricAggregationType metricAggregationType
  }
}

enum AdjustmentType {
  ChangeInCapacity, ExactCapacity, PercentChangeInCapacity
}

enum MetricAggregationType {
  Minimum, Maximum, Average
}
