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

import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * Description for resizing a Proxmox server group. Scale-up clones additional instances from the
 * template recorded on existing members (spinnaker-template tag); scale-down stops and deletes the
 * newest members first.
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class ProxmoxResizeDescription extends ProxmoxBaseDescription {

  private String serverGroupName;

  /** Spinnaker region (Proxmox node) the server group lives on. */
  private String region;

  private Capacity capacity = new Capacity();

  /**
   * Storage pool for disks of newly cloned members (full clones only). Defaults to {@code
   * "local-lvm"}.
   */
  private String storage = "local-lvm";

  /** When {@code true} (default), scale-up performs full independent clones. */
  private boolean fullClone = true;

  /** Whether newly cloned members are started after configuration. Defaults to {@code true}. */
  private boolean startAfterClone = true;

  @Data
  public static class Capacity {
    private Integer min;
    private Integer max;
    private Integer desired;
  }
}
