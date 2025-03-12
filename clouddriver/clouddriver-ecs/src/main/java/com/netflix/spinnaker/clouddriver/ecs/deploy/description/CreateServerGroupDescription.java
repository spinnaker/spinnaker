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

import com.amazonaws.services.ecs.model.CapacityProviderStrategyItem;
import com.amazonaws.services.ecs.model.PlacementConstraint;
import com.amazonaws.services.ecs.model.PlacementStrategy;
import com.netflix.spinnaker.clouddriver.model.ServerGroup;
import com.netflix.spinnaker.kork.artifacts.model.Artifact;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.annotation.Nullable;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = false)
public class CreateServerGroupDescription extends AbstractECSDescription {
  String ecsClusterName;
  String iamRole;

  /**
   * @deprecated this field only allows for one container port to be specified. ECS supports the
   *     ability to have multiple target groups and container ports to be mapped to a container.
   *     <p>This field is deprecated in favour of [targetGroupMappings.containerPort]
   */
  @Deprecated Integer containerPort;

  /**
   * @deprecated this field only allows for one target group to be specified. ECS supports the
   *     ability to have multiple target groups and container ports to be mapped to a container.
   *     <p>This field is deprecated in favour of [targetGroupMappings.targetGroup]
   */
  @Deprecated String targetGroup;

  List<String> securityGroupNames;

  String portProtocol;

  @Nullable Integer computeUnits;
  @Nullable Integer reservedMemory;

  Map<String, String> environmentVariables;
  Map<String, String> tags;

  @Nullable String dockerImageAddress;
  String dockerImageCredentialsSecret;

  ServerGroup.Capacity capacity;

  Map<String, List<String>> availabilityZones;

  boolean copySourceScalingPoliciesAndActions = true;
  Source source = new Source();

  List<PlacementStrategy> placementStrategySequence;
  List<PlacementConstraint> placementConstraints;
  String networkMode;

  /**
   * @deprecated this field only allows for one subnetType where as ECS supports the ability to
   *     deploy to multiple subnets.
   */
  @Deprecated String subnetType;

  Set<String> subnetTypes;

  Boolean associatePublicIpAddress;
  Integer healthCheckGracePeriodSeconds;

  String launchType;
  String platformVersion;

  String logDriver;
  Map<String, String> logOptions;
  Map<String, String> dockerLabels;

  List<ServiceDiscoveryAssociation> serviceDiscoveryAssociations;

  boolean useTaskDefinitionArtifact;
  boolean evaluateTaskDefinitionArtifactExpressions;
  Artifact resolvedTaskDefinitionArtifact;
  Map<Object, Object> spelProcessedTaskDefinitionArtifact;
  String taskDefinitionArtifactAccount;
  Map<String, String> containerToImageMap;
  boolean enableExecuteCommand;
  boolean enableDeploymentCircuitBreaker;

  /**
   * @deprecated this field only allows for one container to be specified. ECS supports the ability
   *     to have multiple target groups and container ports to be mapped to one or more containers.
   *     <p>This field is deprecated in favour of [targetGroupMappings.containerName]
   */
  @Deprecated String loadBalancedContainer;

  Set<TargetGroupProperties> targetGroupMappings;

  List<CapacityProviderStrategyItem> capacityProviderStrategy;

  @Override
  public String getRegion() {
    // CreateServerGroupDescription does not contain a region. Instead it has AvailabilityZones
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

  @Data
  @EqualsAndHashCode(callSuper = false)
  public static class ServiceDiscoveryAssociation {
    ServiceRegistry registry;
    Integer containerPort;
    String containerName;
  }

  @Data
  @EqualsAndHashCode(callSuper = false)
  public static class ServiceRegistry {
    String arn;
    String name;
    String id;
  }

  @Data
  @EqualsAndHashCode(callSuper = false)
  public static class TargetGroupProperties {
    String containerName;
    Integer containerPort;
    String targetGroup;
  }
}
