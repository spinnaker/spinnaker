/*
 * Copyright 2019 THL A29 Limited, a Tencent company.
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

package com.netflix.spinnaker.clouddriver.tencentcloud.model;

import static com.netflix.spinnaker.clouddriver.model.HealthState.*;

import com.netflix.spinnaker.clouddriver.model.Health;
import com.netflix.spinnaker.clouddriver.model.HealthState;
import java.util.ArrayList;
import java.util.List;
import lombok.Data;

@Data
public class TencentCloudTargetHealth implements Health {

  private TargetHealthStatus targetHealthStatus;
  private List<LBHealthSummary> loadBalancers = new ArrayList<>();

  public TencentCloudTargetHealth(boolean healthStatus) {
    targetHealthStatus = healthStatus ? TargetHealthStatus.HEALTHY : TargetHealthStatus.UNHEALTHY;
  }

  public TencentCloudTargetHealth() {
    targetHealthStatus = TargetHealthStatus.UNKNOWN;
  }

  public HealthState getState() {
    switch (targetHealthStatus) {
      case UNHEALTHY:
      case UNKNOWN:
        return Down;
      case HEALTHY:
        return Up;
      default:
        return Unknown;
    }
  }

  public final String getType() {
    return "LoadBalancer";
  }

  public enum TargetHealthStatus {
    UNHEALTHY,
    HEALTHY,
    UNKNOWN;

    public LBHealthSummary.ServiceStatus toServiceStatus() {
      if (this == TargetHealthStatus.HEALTHY) {
        return LBHealthSummary.ServiceStatus.InService;
      }
      return LBHealthSummary.ServiceStatus.OutOfService;
    }
  }

  @Data
  public static class LBHealthSummary {

    private String loadBalancerName;
    private ServiceStatus state;

    public String getDescription() {
      return state.equals(ServiceStatus.OutOfService)
          ? "Instance has failed at least the Unhealthy Threshold number of health checks consecutively."
          : "Healthy";
    }

    public enum ServiceStatus {
      InService,
      OutOfService
    }
  }
}
