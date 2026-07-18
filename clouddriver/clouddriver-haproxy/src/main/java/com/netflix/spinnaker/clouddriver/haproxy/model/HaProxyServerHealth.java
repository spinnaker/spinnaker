/*
 * Copyright 2026 McIntosh.farm
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
package com.netflix.spinnaker.clouddriver.haproxy.model;

import com.netflix.spinnaker.clouddriver.model.HealthState;
import java.util.LinkedHashMap;
import java.util.Map;

/** Maps HAProxy runtime server state to Spinnaker {@link HealthState}. */
public final class HaProxyServerHealth {

  public static final String HEALTH_TYPE = "HaProxyServer";

  private HaProxyServerHealth() {}

  /**
   * Derives the Spinnaker health state from a server's runtime state. The operator-set admin state
   * ({@code maint}/{@code drain}) takes precedence over the observed operational state.
   */
  public static HealthState healthState(String adminState, String operationalState) {
    if ("maint".equals(adminState)) {
      return HealthState.OutOfService;
    }
    if ("drain".equals(adminState)) {
      return HealthState.Draining;
    }
    if (operationalState == null) {
      return HealthState.Unknown;
    }
    switch (operationalState) {
      case "up":
        return HealthState.Up;
      case "down":
        return HealthState.Down;
      case "stopping":
        return HealthState.Draining;
      default:
        return HealthState.Unknown;
    }
  }

  /** The health map attached to load balancer instances and health cache entries. */
  public static Map<String, Object> healthMap(
      String adminState, String operationalState, Object checkStatus) {
    Map<String, Object> health = new LinkedHashMap<>();
    health.put("type", HEALTH_TYPE);
    health.put("state", healthState(adminState, operationalState).name());
    if (adminState != null) {
      health.put("adminState", adminState);
    }
    if (operationalState != null) {
      health.put("operationalState", operationalState);
    }
    if (checkStatus != null) {
      health.put("checkStatus", checkStatus);
    }
    return health;
  }
}
