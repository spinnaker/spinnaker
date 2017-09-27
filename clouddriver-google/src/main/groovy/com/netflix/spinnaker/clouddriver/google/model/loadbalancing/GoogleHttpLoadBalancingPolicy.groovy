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

import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.google.api.services.compute.model.NamedPort

/**
 * For Http(s), balancingMode must be either UTILIZATION or RATE.
 * maxRatePerInstance must be set if RATE, and maxUtilization must be set if UTILIZATION.
 *
 * For Ssl/Tcp, balancingMode must be either UTILIZATION or CONNECTION.
 * maxUtilization must be set if UTILIZATION, maxConnectionsPerInstance if CONNECTION.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
class GoogleHttpLoadBalancingPolicy extends GoogleLoadBalancingPolicy {
  @JsonIgnore
  static final String HTTP_DEFAULT_PORT_NAME = 'http'

  @JsonIgnore
  static final Integer HTTP_DEFAULT_PORT = 80

  Float maxRatePerInstance

  Float maxUtilization

  Float maxConnectionsPerInstance

  @Deprecated
  Integer listeningPort

  /**
   * Additional scaler option that sets the current max usage of the server group for either balancingMode.
   * Valid values are 0.0 through 1.0.
   * https://cloud.google.com/compute/docs/load-balancing/http/backend-service#add_instance_groups_to_a_backend_service
   */
  Float capacityScaler

  /**
   * List of named ports load balancers use to forward traffic to server groups.
   */
  List<NamedPort> namedPorts
}
