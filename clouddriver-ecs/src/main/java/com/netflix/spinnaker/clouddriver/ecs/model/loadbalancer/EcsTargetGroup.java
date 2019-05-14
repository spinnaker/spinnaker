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

package com.netflix.spinnaker.clouddriver.ecs.model.loadbalancer;

import com.netflix.spinnaker.clouddriver.model.LoadBalancerProvider;
import java.util.List;
import java.util.Map;
import lombok.Data;

@Data
public class EcsTargetGroup implements LoadBalancerProvider.Details {
  // TODO - add fields we want to persist from the cached value for target groups

  List<String> loadBalancerNames;
  List<String> instances;
  Integer healthCheckTimeoutSeconds;
  String targetGroupArn;
  String healthCheckPort;
  Map<String, String> matcher;
  String healthCheckProtocol;
  String targetGroupName;
  String healthCheckPath;
  String protocol;
  Integer port;
  Integer healthCheckIntervalSeconds;
  Integer healthyThresholdCount;
  String vpcId;
  Integer unhealthyThresholdCount;
  Map<String, String> attributes;
}
