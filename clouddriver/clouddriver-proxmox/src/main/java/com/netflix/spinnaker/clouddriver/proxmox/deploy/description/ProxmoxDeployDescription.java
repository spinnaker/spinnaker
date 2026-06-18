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

/** Description for creating a new Proxmox VM or LXC container. */
@Data
@EqualsAndHashCode(callSuper = true)
public class ProxmoxDeployDescription extends ProxmoxBaseDescription {
  private String node;

  /** {@code "qemu"} for KVM VMs, {@code "lxc"} for containers. */
  private String vmType = "qemu";

  /** Proxmox VM ID. When null, Proxmox assigns the next available ID. */
  private Integer vmid;

  /** VM/container hostname or display name. */
  private String name;

  /** Spinnaker moniker — app/cluster/stack/detail/sequence. Tags are derived from this. */
  private Moniker moniker;

  private int memory = 512;
  private int cores = 1;

  /** Number of CPU sockets (QEMU only). */
  private int sockets = 1;

  /** Storage pool for the boot disk (e.g. {@code "local-lvm"}). */
  private String storage = "local-lvm";

  /** Boot disk size in GB. */
  private int diskSize = 8;

  /** Boot disk format for QEMU (e.g. {@code "raw"}, {@code "qcow2"}). */
  private String diskFormat = "raw";

  /** SCSI controller model for QEMU (e.g. {@code "virtio-scsi-pci"}, {@code "lsi"}). */
  private String scsiHw = "virtio-scsi-pci";

  /** Network config string (e.g. {@code "virtio,bridge=vmbr0"}). */
  private String net0 = "virtio,bridge=vmbr0";

  /**
   * LXC only — CT template path (e.g. {@code
   * "local:vztmpl/ubuntu-22.04-standard_22.04-1_amd64.tar.zst"}).
   */
  private String osTemplate;

  /** QEMU only — ISO to mount as CD-ROM (e.g. {@code "local:iso/ubuntu-22.04.iso"}). */
  private String cdrom;

  /**
   * Extra Proxmox tags to include alongside the moniker-derived tags (semicolon-separated {@code
   * category+value} pairs). Moniker tags take precedence over any conflicting entries here.
   */
  private String tags;

  /**
   * Additional raw Proxmox API parameters passed verbatim to the create call (e.g. {@code
   * "balloon": "0", "onboot": "1"}). These take precedence over all named fields above.
   */
  private Map<String, String> additionalOptions = new HashMap<>();
}
