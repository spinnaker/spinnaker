/*
 * Copyright 2016 Google, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
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
package com.netflix.spinnaker.clouddriver.google.model.loadbalancing

class GoogleHttpLoadBalancingPolicy {
  static final String HTTP_PORT_NAME = 'http'
  static final Integer HTTP_DEFAULT_PORT = 80
  /**
   * Describes the metric used to determine the serving capacity of the serverGroup.
   * Either UTILIZATION or RATE. maxRatePerInstance must be set if RATE, and
   * maxUtilization must be set if UTILIZATION.
   */
  BalancingMode balancingMode

  Float maxRatePerInstance

  Float maxUtilization

  /**
   * Additional scaler option that sets the current max usage of the server group for either balancingMode.
   * Valid values are 0.0 through 1.0.
   * https://cloud.google.com/compute/docs/load-balancing/http/backend-service#add_instance_groups_to_a_backend_service
   */
  Float capacityScaler

  /**
   * Port that the HTTP LB will forward traffic to on the server group.
   */
  Integer listeningPort

  static enum BalancingMode {
    UTILIZATION,
    RATE
  }
}
