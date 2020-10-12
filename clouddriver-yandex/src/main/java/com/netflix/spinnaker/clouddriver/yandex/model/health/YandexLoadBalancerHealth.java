/*
 * Copyright 2020 YANDEX LLC
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

package com.netflix.spinnaker.clouddriver.yandex.model.health;

import com.netflix.spinnaker.clouddriver.model.Health;
import com.netflix.spinnaker.clouddriver.model.HealthState;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class YandexLoadBalancerHealth implements Health {
  private String address;
  private String subnetId;
  private Status status;

  @Override
  public HealthState getState() {
    return status.toHeathState();
  }

  public enum Status {
    INITIAL,
    HEALTHY,
    UNHEALTHY,
    DRAINING,
    INACTIVE;

    public HealthState toHeathState() {
      if (this == Status.HEALTHY) {
        return HealthState.Up;
      }
      return HealthState.Down;
    }

    public ServiceStatus toServiceStatus() {
      if (this == Status.HEALTHY) {
        return ServiceStatus.InService;
      }
      return ServiceStatus.OutOfService;
    }

    public enum ServiceStatus {
      InService,
      OutOfService;
    }
  }
}
