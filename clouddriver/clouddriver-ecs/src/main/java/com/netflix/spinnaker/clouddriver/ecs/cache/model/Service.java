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

package com.netflix.spinnaker.clouddriver.ecs.cache.model;

import com.netflix.spinnaker.moniker.Moniker;
import java.util.List;
import lombok.Data;
import software.amazon.awssdk.services.ecs.model.LoadBalancer;

@Data
public class Service {
  String account;
  String region;
  String applicationName;
  String serviceName;
  String serviceArn;
  String clusterName;
  String clusterArn;
  String roleArn;
  String taskDefinition;
  int desiredCount;
  int maximumPercent;
  int minimumHealthyPercent;
  List<LoadBalancer> loadBalancers;
  List<String> subnets;
  List<String> securityGroups;
  long createdAt;
  Moniker moniker;

  // ECS's own rollout state for the service's PRIMARY deployment, captured from the same
  // DescribeServices response the caching agent already fetches (deployments() list). Surfaced so
  // Deck's clusters/details views can show whether ECS considers the current deployment settled --
  // most useful for the ecs-native provider, whose single durable service has no vNNN sequence.
  // Null when no PRIMARY deployment was present at caching time.
  String deploymentId;
  String rolloutState;
  String rolloutStateReason;

  // True only when ECS service tags contain the durable ecs-native ownership marker.
  boolean ecsNative;
}
