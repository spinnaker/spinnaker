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
package com.netflix.spinnaker.clouddriver.proxmox.deploy.ops;

import com.netflix.spinnaker.clouddriver.proxmox.client.ProxmoxApiService;
import com.netflix.spinnaker.clouddriver.proxmox.deploy.description.ProxmoxResourceDescription;
import java.util.List;
import java.util.Map;

/** Reboots a running QEMU VM or LXC container. */
public class RebootProxmoxInstancesAtomicOperation extends AbstractProxmoxAtomicOperation<Void> {

  private static final String PHASE = "REBOOT_PROXMOX_INSTANCES";

  private final ProxmoxResourceDescription description;

  public RebootProxmoxInstancesAtomicOperation(ProxmoxResourceDescription description) {
    super(PHASE);
    this.description = description;
  }

  @Override
  public Void operate(List priorOutputs) {
    ProxmoxApiService api = description.getApiService();
    String node = description.getNode();
    int vmid = description.getVmid();
    String vmType = description.getVmType();

    updateStatus("Rebooting " + vmType + " " + vmid + " on " + node);
    String upid;
    if ("lxc".equals(vmType)) {
      upid = executeCall(api.rebootLxc(node, vmid, Map.of()));
    } else {
      upid = executeCall(api.rebootVm(node, vmid, Map.of()));
    }
    if (upid != null) {
      pollTaskUntilDone(api, node, upid);
    }

    updateStatus("Rebooted " + vmType + " " + vmid);
    return null;
  }
}
