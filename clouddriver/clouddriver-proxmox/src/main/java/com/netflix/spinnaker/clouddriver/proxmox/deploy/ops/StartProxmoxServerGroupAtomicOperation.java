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

public class StartProxmoxServerGroupAtomicOperation extends AbstractProxmoxAtomicOperation<Void> {

  private static final String PHASE = "START_PROXMOX_SERVER_GROUP";

  private final ProxmoxResourceDescription description;

  public StartProxmoxServerGroupAtomicOperation(ProxmoxResourceDescription description) {
    super(PHASE);
    this.description = description;
  }

  @Override
  public Void operate(List priorOutputs) {
    ProxmoxApiService api = description.getApiService();

    if (description.getServerGroupName() != null) {
      String region =
          description.getRegion() != null ? description.getRegion() : description.getNode();
      List<ProxmoxServerGroupMembers.Member> members =
          ProxmoxServerGroupMembers.resolve(api, description.getServerGroupName(), region);
      updateStatus(
          "Starting server group "
              + description.getServerGroupName()
              + " ("
              + members.size()
              + " member(s))");
      for (ProxmoxServerGroupMembers.Member member : members) {
        startOne(api, member.node(), member.vmid(), member.vmType());
      }
      updateStatus("Started server group " + description.getServerGroupName());
    } else {
      startOne(api, description.getNode(), description.getVmid(), description.getVmType());
    }
    return null;
  }

  private void startOne(ProxmoxApiService api, String node, int vmid, String vmType) {
    updateStatus("Starting " + vmType + " " + vmid + " on " + node);
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
    updateStatus("Started " + vmType + " " + vmid);
  }
}
