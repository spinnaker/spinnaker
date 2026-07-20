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
package com.netflix.spinnaker.clouddriver.proxmox.deploy.description;

import java.util.List;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** Description for operations that target an existing VM or LXC container by node + vmid. */
@Data
@EqualsAndHashCode(callSuper = true)
public class ProxmoxResourceDescription extends ProxmoxBaseDescription {
  private String node;
  private int vmid;

  /** {@code "qemu"} for KVM VMs, {@code "lxc"} for containers. */
  private String vmType = "qemu";

  /**
   * When set, the operation applies to every member of the server group (resolved via the
   * spinnaker-server-group tag, or VM name for single-VM groups) instead of a single {@link #vmid}.
   * This is the shape orca's generic server group tasks submit.
   */
  private String serverGroupName;

  /** Spinnaker region (Proxmox node); used with {@link #serverGroupName} to scope resolution. */
  private String region;

  /**
   * Instance names (Spinnaker instance ids are Proxmox VM/container names). When set, the operation
   * applies to exactly these instances; each is resolved to its node and vmid.
   */
  private List<String> instanceIds;
}
