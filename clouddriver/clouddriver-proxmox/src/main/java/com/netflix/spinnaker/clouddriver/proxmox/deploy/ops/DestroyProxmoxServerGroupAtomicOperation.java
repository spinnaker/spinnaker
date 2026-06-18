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

/** Stops and permanently deletes a QEMU VM or LXC container. */
public class DestroyProxmoxServerGroupAtomicOperation extends AbstractProxmoxAtomicOperation<Void> {

  private static final String PHASE = "DESTROY_PROXMOX_SERVER_GROUP";

  private final ProxmoxResourceDescription description;

  public DestroyProxmoxServerGroupAtomicOperation(ProxmoxResourceDescription description) {
    super(PHASE);
    this.description = description;
  }

  @Override
  public Void operate(List priorOutputs) {
    ProxmoxApiService api = description.getApiService();
    String node = description.getNode();
    int vmid = description.getVmid();
    String vmType = description.getVmType();

    updateStatus("Stopping " + vmType + " " + vmid + " on " + node + " before deletion");
    String stopUpid;
    if ("lxc".equals(vmType)) {
      stopUpid = executeCall(api.stopLxc(node, vmid, Map.of()));
    } else {
      stopUpid = executeCall(api.stopVm(node, vmid, Map.of()));
    }
    // Stop failure is tolerated — the resource may already be stopped
    if (stopUpid != null) {
      pollTaskIgnoringFailure(api, node, stopUpid);
    }

    updateStatus("Deleting " + vmType + " " + vmid + " on " + node);
    String deleteUpid;
    if ("lxc".equals(vmType)) {
      deleteUpid = executeCall(api.deleteLxc(node, vmid));
    } else {
      deleteUpid = executeCall(api.deleteVm(node, vmid));
    }
    if (deleteUpid != null) {
      pollTaskUntilDone(api, node, deleteUpid);
    }

    updateStatus("Deleted " + vmType + " " + vmid);
    return null;
  }
}
