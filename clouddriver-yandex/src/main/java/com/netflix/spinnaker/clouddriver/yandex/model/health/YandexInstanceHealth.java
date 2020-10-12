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
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class YandexInstanceHealth implements Health {
  private Status status;

  public HealthState getState() {
    return status.toHealthState();
  }

  public enum Status {
    PROVISIONING,
    RUNNING,
    STOPPING,
    STOPPED,
    STARTING,
    RESTARTING,
    UPDATING,
    ERROR,
    CRASHED,
    DELETING;

    public HealthState toHealthState() {
      switch (this) {
        case PROVISIONING:
        case STARTING:
          return HealthState.Starting;
        case RUNNING:
          return HealthState.Unknown;
        case ERROR:
        case CRASHED:
          return HealthState.Failed;
        default:
          return HealthState.Down;
      }
    }
  }
}
