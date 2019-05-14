/*
 * Copyright 2017 Lookout, Inc.
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

package com.netflix.spinnaker.clouddriver.ecs.model;

import com.netflix.spinnaker.clouddriver.model.Instance;
import com.netflix.spinnaker.clouddriver.model.ServerGroup;
import java.util.Map;
import java.util.Set;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class EcsServerGroup implements ServerGroup {

  String name;
  String type;
  String cloudProvider;
  String region;
  Boolean disabled;
  Long createdTime;
  Set<String> zones;
  Set<Instance> instances;
  Set<String> loadBalancers;
  Set<String> securityGroups;
  Map<String, Object> launchConfig;
  InstanceCounts instanceCounts;
  Capacity capacity;
  ImagesSummary imagesSummary;
  ImageSummary imageSummary;
  Map<String, Object> tags;
  String ecsCluster;
  TaskDefinition taskDefinition;
  String vpcId;
  AutoScalingGroup asg;
  Set<String> metricAlarms;

  @Override
  public Boolean isDisabled() {
    return disabled;
  }

  @Data
  @NoArgsConstructor
  public static class AutoScalingGroup {
    Integer minSize;
    Integer maxSize;
    Integer desiredCapacity;
  }
}
