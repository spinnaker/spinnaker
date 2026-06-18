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

import java.util.HashMap;
import java.util.Map;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** Description for updating the configuration of an existing VM or LXC container. */
@Data
@EqualsAndHashCode(callSuper = true)
public class ProxmoxUpdateSettingsDescription extends ProxmoxBaseDescription {
  private String node;
  private int vmid;

  /** {@code "qemu"} for KVM VMs, {@code "lxc"} for containers. */
  private String vmType = "qemu";

  /**
   * Raw Proxmox API configuration parameters to apply (e.g. {@code "memory": "1024", "cores":
   * "2"}). Keys and values are passed verbatim to the Proxmox config endpoint.
   */
  private Map<String, String> settings = new HashMap<>();
}
