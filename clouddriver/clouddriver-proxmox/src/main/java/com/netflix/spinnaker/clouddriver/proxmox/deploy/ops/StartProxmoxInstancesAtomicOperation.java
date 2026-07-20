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

/** Starts individual stopped VMs/containers. */
public class StartProxmoxInstancesAtomicOperation extends AbstractProxmoxInstancesAtomicOperation {

  public StartProxmoxInstancesAtomicOperation(ProxmoxResourceDescription description) {
    super("START_PROXMOX_INSTANCES", description);
  }

  @Override
  protected String verb() {
    return "Starting";
  }

  @Override
  protected void applyTo(ProxmoxApiService api, String node, int vmid, String vmType) {
    String upid;
    if ("lxc".equals(vmType)) {
      upid = executeCall(api.startLxc(node, vmid, Map.of()));
    } else {
      upid = executeCall(api.startVm(node, vmid, Map.of()));
    }
    // Start failure is tolerated — the resource may already be running
    if (upid != null) {
      pollTaskIgnoringFailure(api, node, upid);
    }
    updateStatus("Started " + vmType + " " + vmid + " on " + node);
  }
}
