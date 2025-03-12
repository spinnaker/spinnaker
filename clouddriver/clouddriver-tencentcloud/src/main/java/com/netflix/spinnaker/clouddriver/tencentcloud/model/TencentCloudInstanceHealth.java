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
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class TencentCloudInstanceHealth implements Health {

  private final String healthClass = "platform";
  private final String type = "TencentCloud";
  private Status instanceStatus;

  public TencentCloudInstanceHealth(String instanceStatus) {
    this.instanceStatus = Enum.valueOf(Status.class, instanceStatus);
  }

  public HealthState getState() {
    switch (instanceStatus) {
      case PENDING:
        return Starting;
      case RUNNING:
        return Unknown;
      case STOPPED:
        return Down;
      default:
        return Unknown;
    }
  }

  public enum Status {
    PENDING,
    LAUNCH_FAILED,
    RUNNING,
    STOPPED,
    STARTING,
    STOPPING,
    REBOOTING,
    SHUTDOWN,
    TERMINATING
  }
}
