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
import com.netflix.spinnaker.clouddriver.proxmox.deploy.description.ProxmoxUpdateSettingsDescription;
import java.util.List;

/**
 * Applies configuration changes to an existing QEMU VM or LXC container.
 *
 * <p>Settings are passed as raw Proxmox API parameter names (e.g. {@code "memory"}, {@code
 * "cores"}, {@code "tags"}). Some changes take effect immediately; others require a reboot of the
 * VM.
 */
public class UpdateProxmoxSettingsAtomicOperation extends AbstractProxmoxAtomicOperation<Void> {

  private static final String PHASE = "UPDATE_PROXMOX_SETTINGS";

  private final ProxmoxUpdateSettingsDescription description;

  public UpdateProxmoxSettingsAtomicOperation(ProxmoxUpdateSettingsDescription description) {
    super(PHASE);
    this.description = description;
  }

  @Override
  public Void operate(List priorOutputs) {
    ProxmoxApiService api = description.getApiService();
    String node = description.getNode();
    int vmid = description.getVmid();
    String vmType = description.getVmType();

    updateStatus(
        "Updating settings on "
            + vmType
            + " "
            + vmid
            + " on "
            + node
            + ": "
            + description.getSettings().keySet());

    String upid;
    if ("lxc".equals(vmType)) {
      upid = executeCall(api.updateLxcConfig(node, vmid, description.getSettings()));
    } else {
      upid = executeCall(api.updateVmConfig(node, vmid, description.getSettings()));
    }

    // Config updates may return a UPID (e.g. when a snapshot/agent interaction is needed)
    // or null when the change is applied synchronously.
    if (upid != null && upid.startsWith("UPID:")) {
      pollTaskUntilDone(api, node, upid);
    }

    updateStatus("Settings updated on " + vmType + " " + vmid);
    return null;
  }
}
