/*
 * Copyright 2018 Lookout, Inc.
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

package com.netflix.spinnaker.clouddriver.ecs.deploy.description;

import com.amazonaws.services.cloudwatch.model.MetricAlarm;
import com.amazonaws.services.ecs.model.PlacementStrategy;
import com.amazonaws.services.ecs.model.PlacementConstraint;
import com.netflix.spinnaker.clouddriver.model.ServerGroup;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;
import java.util.Map;

@Data
@EqualsAndHashCode(callSuper = false)
public class CreateServerGroupDescription extends AbstractECSDescription {
  String ecsClusterName;
  String iamRole;
  Integer containerPort;
  String targetGroup;
  List<String> securityGroupNames;

  String portProtocol;

  Integer computeUnits;
  Integer reservedMemory;

  Map<String, String> environmentVariables;
  Map<String, String> tags;

  String dockerImageAddress;
  String dockerImageCredentialsSecret;

  ServerGroup.Capacity capacity;

  Map<String, List<String>> availabilityZones;

  boolean copySourceScalingPoliciesAndActions = true;
  Source source = new Source();

  List<PlacementStrategy> placementStrategySequence;
  List<PlacementConstraint> placementConstraints;
  String networkMode;
  String subnetType;
  Boolean associatePublicIpAddress;
  Integer healthCheckGracePeriodSeconds;

  String launchType;
  String platformVersion;

  String logDriver;
  Map<String, String> logOptions;
  Map<String, String> dockerLabels;

  @Override
  public String getRegion() {
    //CreateServerGroupDescription does not contain a region. Instead it has AvailabilityZones
    return getAvailabilityZones().keySet().iterator().next();
  }

  @Data
  @EqualsAndHashCode(callSuper = false)
  public static class Source {
    String account;
    String region;
    String asgName;
    Boolean useSourceCapacity;
  }
}
