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

  /** {@code "qemu"} for KVM VMs, {@code "lxc"} for containers. */
  private String vmType;

  /** Uptime in seconds; 0 when stopped. */
  private Long uptimeSeconds;

  /** Current CPU usage as a fraction of allocated vCPUs (0.0 – cpus). */
  private Double cpuUsage;

  /** Memory currently in use, in MB. */
  private Long memoryUsedMb;

  /** Root disk usage in GB. */
  private Long diskUsedGb;

  private Integer sockets;
  private Integer cores;
  private String machine;
  private String bios;
  private String bootOrder;
  private String scsiController;

  /** Primary network interface config string (net0). */
  private String net0;

  /** Primary disk config string (scsi0 for VMs, rootfs for containers). */
  private String disk0;

  /**
   * All disk-like config entries keyed by device name (scsi*, sata*, virtio*, ide*, efidisk*,
   * tpmstate*, unused* for VMs; rootfs, mp*, unused* for containers).
   */
  private Map<String, String> disks;

  private String description;

  /** Proxmox tags parsed into category/value pairs (Spinnaker moniker tags included). */
  private Map<String, String> tags;

  private Boolean agentEnabled;
  private Boolean onBoot;
  private Boolean protection;
  private String qmpStatus;
  private Boolean haManaged;

  /** LXC swap currently in use, in MB. */
  private Long swapUsedMb;

  /** LXC swap limit, in MB. */
  private Long swapMb;

  private Long networkInBytes;
  private Long networkOutBytes;
  private Long diskReadBytes;
  private Long diskWriteBytes;

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
