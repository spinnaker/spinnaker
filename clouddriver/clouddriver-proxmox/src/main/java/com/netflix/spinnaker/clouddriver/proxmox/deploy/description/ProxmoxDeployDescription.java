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

import com.netflix.spinnaker.moniker.Moniker;
import java.util.HashMap;
import java.util.Map;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** Description for cloning a Proxmox VM or LXC template into a new server group. */
@Data
@EqualsAndHashCode(callSuper = true)
public class ProxmoxDeployDescription extends ProxmoxBaseDescription {
  private String node;

  /** {@code "qemu"} for KVM VMs, {@code "lxc"} for containers. */
  private String vmType = "qemu";

  /** VMID of the source template to clone. Required. */
  private int templateVmid;

  /**
   * Node where the template resides. Defaults to {@link #node} when null, allowing cross-node
   * clones when the template lives on a different host.
   */
  private String templateNode;

  /** When {@code true} (default), perform a full independent clone; otherwise a linked clone. */
  private boolean fullClone = true;

  /** Proxmox VM ID for the new instance. When null, Proxmox assigns the next available ID. */
  private Integer vmid;

  /** VM/container hostname or display name. */
  private String name;

  /** Spinnaker moniker — app/cluster/stack/detail/sequence. Tags are derived from this. */
  private Moniker moniker;

  /** Storage pool for the cloned disk (e.g. {@code "local-lvm"}). Required for full clones. */
  private String storage = "local-lvm";

  /** Memory override in MB applied after clone. */
  private int memory = 512;

  /** CPU core count override applied after clone. */
  private int cores = 1;

  /** CPU socket count override applied after clone (QEMU only). */
  private int sockets = 1;

  /** Network config override applied after clone (e.g. {@code "virtio,bridge=vmbr0"}). */
  private String net0 = "virtio,bridge=vmbr0";

  /**
   * Extra Proxmox tags merged with moniker-derived tags. Moniker tags take precedence over any
   * conflicting entries here (semicolon-separated {@code category+value} pairs).
   */
  private String tags;

  /**
   * Additional raw Proxmox API parameters passed verbatim to the config-update call after clone.
   * These take precedence over all named fields above.
   */
  private Map<String, String> additionalOptions = new HashMap<>();
}
