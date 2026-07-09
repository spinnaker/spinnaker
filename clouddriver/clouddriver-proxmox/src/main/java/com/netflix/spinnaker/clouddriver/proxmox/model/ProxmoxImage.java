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
package com.netflix.spinnaker.clouddriver.proxmox.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** A Proxmox VM or LXC template exposed as a Spinnaker image. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProxmoxImage implements com.netflix.spinnaker.clouddriver.model.Image {

  /** Cache key: {@code proxmox;IMAGE;account;node;vmid}. */
  private String id;

  /** Template display name. */
  private String name;

  /** Node the template resides on (used as the Spinnaker region). */
  private String region;

  /** Proxmox account this template belongs to. */
  private String account;

  /** Proxmox VMID of the template. */
  private Integer vmId;

  /**
   * Virtualisation type: {@code "qemu"} for VM templates, {@code "lxc"} for container templates.
   */
  private String vmType;
}
