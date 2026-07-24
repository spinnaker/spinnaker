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

    if (description.getServerGroupName() != null) {
      // Server-group scoped (orca / deck): destroy every member.
      String region =
          description.getRegion() != null ? description.getRegion() : description.getNode();
      List<ProxmoxServerGroupMembers.Member> members =
          ProxmoxServerGroupMembers.resolve(api, description.getServerGroupName(), region);
      updateStatus(
          "Destroying server group "
              + description.getServerGroupName()
              + " ("
              + members.size()
              + " member(s))");
      for (ProxmoxServerGroupMembers.Member member : members) {
        destroyOne(api, member.node(), member.vmid(), member.vmType());
      }
      updateStatus("Destroyed server group " + description.getServerGroupName());
    } else {
      destroyOne(api, description.getNode(), description.getVmid(), description.getVmType());
    }
    return null;
  }

  private void destroyOne(ProxmoxApiService api, String node, int vmid, String vmType) {
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
  }
}
