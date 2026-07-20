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
import java.util.Map;

/** Stops and permanently deletes individual VMs/containers. */
public class TerminateProxmoxInstancesAtomicOperation
    extends AbstractProxmoxInstancesAtomicOperation {

  public TerminateProxmoxInstancesAtomicOperation(ProxmoxResourceDescription description) {
    super("TERMINATE_PROXMOX_INSTANCES", description);
  }

  @Override
  protected String verb() {
    return "Terminating";
  }

  @Override
  protected void applyTo(ProxmoxApiService api, String node, int vmid, String vmType) {
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

    String deleteUpid;
    if ("lxc".equals(vmType)) {
      deleteUpid = executeCall(api.deleteLxc(node, vmid));
    } else {
      deleteUpid = executeCall(api.deleteVm(node, vmid));
    }
    if (deleteUpid != null) {
      pollTaskUntilDone(api, node, deleteUpid);
    }
    updateStatus("Terminated " + vmType + " " + vmid + " on " + node);
  }
}
