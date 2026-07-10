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
package com.netflix.spinnaker.clouddriver.proxmox.provider.view;

import com.netflix.spinnaker.clouddriver.model.HealthState;
import com.netflix.spinnaker.clouddriver.model.Instance;
import com.netflix.spinnaker.clouddriver.proxmox.ProxmoxProvider;
import java.util.List;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProxmoxInstance implements Instance {
  private String name;
  private String zone;
  private Long launchTime;
  private HealthState healthState;
  private List<Map<String, Object>> health;
  private Integer vmId;
  private Integer cpus;
  private Long memoryMb;
  private Long diskGb;
  private String osType;
  private String status;

  @Override
  public String getCloudProvider() {
    return ProxmoxProvider.ID;
  }

  public static HealthState healthStateFrom(String status) {
    if (status == null) return HealthState.Unknown;
    return switch (status) {
      case "running" -> HealthState.Up;
      case "stopped" -> HealthState.Down;
      case "paused", "suspended" -> HealthState.OutOfService;
      default -> HealthState.Unknown;
    };
  }
}
