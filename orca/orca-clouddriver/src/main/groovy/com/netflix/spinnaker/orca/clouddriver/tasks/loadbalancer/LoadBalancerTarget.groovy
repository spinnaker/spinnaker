/*
 * Copyright 2026 Harness, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.orca.clouddriver.tasks.loadbalancer

final class LoadBalancerTarget {
  private static final Set<String> REGIONAL_EXTERNAL_TYPES = [
    "EXTERNAL_MANAGED",
    "REGIONAL_EXTERNAL_NETWORK",
  ] as Set<String>

  private LoadBalancerTarget() {}

  static boolean isRegionalExternalType(String loadBalancerType) {
    return REGIONAL_EXTERNAL_TYPES.any { it.equalsIgnoreCase(loadBalancerType) }
  }

  static boolean isRegionalExternal(String cloudProvider, String loadBalancerType) {
    return "gce".equalsIgnoreCase(cloudProvider) && isRegionalExternalType(loadBalancerType)
  }

  static Map<String, Object> fromOperation(String cloudProvider, Map operation, String credentials) {
    Map<String, Object> target = [
      credentials      : credentials,
      availabilityZones: operation.availabilityZones,
      vpcId            : operation.vpcId,
      name             : operation.name,
    ]
    if (isRegionalExternal(cloudProvider, operation.loadBalancerType as String)) {
      target.account = (operation.account ?: operation.credentials ?: credentials) as String
      target.region = operation.region
      target.loadBalancerType = operation.loadBalancerType
      target.loadBalancerName = (operation.loadBalancerName ?: operation.name) as String
    }
    return target
  }
}
