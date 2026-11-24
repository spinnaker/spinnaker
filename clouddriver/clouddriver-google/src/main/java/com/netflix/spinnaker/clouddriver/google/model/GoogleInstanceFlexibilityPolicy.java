/*
 * Copyright 2026 Harness, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.clouddriver.google.model;

import java.util.List;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Instance flexibility policy for specifying multiple machine types with ranked preferences in a
 * MIG. When a preferred machine type is unavailable, the MIG automatically selects another
 * compatible type.
 *
 * @see <a href="https://cloud.google.com/compute/docs/instance-groups/about-instance-flexibility">
 *     GCP Instance Flexibility Documentation</a>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class GoogleInstanceFlexibilityPolicy {

  /** Map of selection name to InstanceSelection, defining ranked machine type preferences. */
  Map<String, InstanceSelection> instanceSelections;

  /** A single instance selection entry with a rank and list of acceptable machine types. */
  @Data
  @NoArgsConstructor
  @AllArgsConstructor
  public static class InstanceSelection {
    /**
     * Rank of this selection group (lower rank = higher preference). Optional; omitted ranks
     * receive equal/default preference in GCP.
     */
    Integer rank;

    /** Machine types acceptable for this selection group. */
    List<String> machineTypes;

    /**
     * Optional minimum CPU platform override (e.g. {@code "Intel Ice Lake"}) applied to VMs created
     * from this selection instead of the server group's {@code minCpuPlatform}.
     */
    String minCpuPlatform;

    /**
     * Optional overrides for the instance template's persistent disks, applied to VMs created from
     * this selection. Use this when the selection's machine types need a different disk type than
     * the template, e.g. N4/C4 machines require Hyperdisk while E2/N2 require Persistent Disk.
     */
    List<DiskOverride> disks;

    public InstanceSelection(Integer rank, List<String> machineTypes) {
      this(rank, machineTypes, null, null);
    }
  }

  /**
   * Override for one of the server group's persistent disks within an instance selection.
   *
   * <p>The target disk is identified by {@code boot: true} or by {@code index} (0-based position
   * among the server group's persistent disks; 0 is the boot disk). Clouddriver resolves the target
   * to the generated instance template disk (whose device name embeds the server group name),
   * copies its configuration (source image, labels, auto-delete, device name) and applies {@code
   * type} and/or {@code sizeGb}, because GCE replaces template disks whose device name matches.
   *
   * @see <a
   *     href="https://cloud.google.com/compute/docs/instance-groups/about-instance-flexibility">How
   *     minCpuPlatform and disks overrides work (GCP docs)</a>
   */
  @Data
  @NoArgsConstructor
  @AllArgsConstructor
  public static class DiskOverride {
    /** Targets the boot disk when true. */
    Boolean boot;

    /** 0-based index among the server group's persistent disks; 0 is the boot disk. */
    Integer index;

    /** Disk type override, e.g. {@code hyperdisk-balanced} or {@code pd-ssd}. */
    String type;

    /** Disk size override in GB. */
    Long sizeGb;
  }
}
