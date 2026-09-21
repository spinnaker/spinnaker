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
  private LoadBalancerTarget() {}

  static Map<String, Object> fromOperation(Map operation, String fallbackAccount = null) {
    String account = (operation.account ?: operation.credentials ?: fallbackAccount) as String
    String region = operation.region as String
    String loadBalancerName = (operation.loadBalancerName ?: operation.name) as String
    Map availabilityZones = operation.availabilityZones as Map
    if (!availabilityZones && region) {
      availabilityZones = [(region): operation.regionZones]
    }

    return [
      credentials      : account,
      account          : account,
      region           : region,
      availabilityZones: availabilityZones,
      loadBalancerType : operation.loadBalancerType,
      loadBalancerName : loadBalancerName,
      name             : loadBalancerName,
      urlMapName       : operation.urlMapName,
      vpcId            : operation.vpcId,
    ]
  }
}
