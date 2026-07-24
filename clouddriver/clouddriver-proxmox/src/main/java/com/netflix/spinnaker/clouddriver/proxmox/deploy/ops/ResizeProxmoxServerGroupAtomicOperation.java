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
import com.netflix.spinnaker.clouddriver.proxmox.deploy.description.ProxmoxResizeDescription;
import com.netflix.spinnaker.clouddriver.proxmox.deploy.ops.ProxmoxServerGroupMembers.Member;
import com.netflix.spinnaker.clouddriver.proxmox.names.ProxmoxTagNamer;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Scales a Proxmox server group to the desired capacity, treating the group as a scaling group.
 *
 * <p>Scale-up clones new instances from the template the existing members were deployed from
 * (recorded in the {@code spinnaker-template} tag) and copies the reference member's tags and
 * compute configuration so new members join the same server group. Scale-down stops and deletes the
 * newest members (highest VMID) first.
 */
public class ResizeProxmoxServerGroupAtomicOperation extends AbstractProxmoxAtomicOperation<Void> {

  private static final String PHASE = "RESIZE_PROXMOX_SERVER_GROUP";

  /** Compute/network config keys copied from the reference member onto new clones. */
  private static final List<String> REPLICATED_CONFIG_KEYS =
      List.of("memory", "cores", "sockets", "net0", "ipconfig0", "tags");

  private final ProxmoxResizeDescription description;

  public ResizeProxmoxServerGroupAtomicOperation(ProxmoxResizeDescription description) {
    super(PHASE);
    this.description = description;
  }

  @Override
  public Void operate(List priorOutputs) {
    ProxmoxApiService api = description.getApiService();
    String serverGroupName = description.getServerGroupName();
    Integer desired =
        description.getCapacity() != null ? description.getCapacity().getDesired() : null;
    if (serverGroupName == null || desired == null) {
      throw new IllegalArgumentException("serverGroupName and capacity.desired are required");
    }

    List<Member> members =
        ProxmoxServerGroupMembers.resolve(api, serverGroupName, description.getRegion());
    updateStatus(
        "Server group "
            + serverGroupName
            + " has "
            + members.size()
            + " member(s); desired capacity is "
            + desired);

    if (members.isEmpty()) {
      throw new IllegalStateException(
          "No members found for server group "
              + serverGroupName
              + "; deploy at least one instance before resizing");
    }

    if (desired > members.size()) {
      scaleUp(api, members, desired - members.size());
    } else if (desired < members.size()) {
      scaleDown(api, members, members.size() - desired);
    } else {
      updateStatus("Server group already at desired capacity; nothing to do");
    }
    return null;
  }

  private void scaleUp(ProxmoxApiService api, List<Member> members, int count) {
    Member reference = members.get(0);
    Map<String, String> refTags = ProxmoxTagNamer.parseTags(reference.tags());
    String templateTag = refTags.get(ProxmoxTagNamer.TEMPLATE_TAG);
    if (templateTag == null) {
      throw new IllegalStateException(
          "Member "
              + reference.vmid()
              + " has no "
              + ProxmoxTagNamer.TEMPLATE_TAG
              + " tag; cannot determine the template to clone for scale-up");
    }
    int templateVmid = Integer.parseInt(templateTag);
    String vmType = reference.vmType();
    String templateNode = ProxmoxServerGroupMembers.findTemplateNode(api, templateVmid, vmType);
    if (templateNode == null) {
      throw new IllegalStateException(
          "Template vmid " + templateVmid + " not found on any node in the cluster");
    }

    // Replicate the reference member's compute/network config (and its tags, which carry
    // server-group membership) onto every new clone.
    Map<String, Object> referenceConfig =
        executeCall(
            "lxc".equals(vmType)
                ? api.getLxcConfig(reference.node(), reference.vmid())
                : api.getVmConfig(reference.node(), reference.vmid()));
    Map<String, String> configParams = new LinkedHashMap<>();
    if (referenceConfig != null) {
      for (String key : REPLICATED_CONFIG_KEYS) {
        Object value = referenceConfig.get(key);
        if (value != null) {
          configParams.put(key, value.toString());
        }
      }
    }
    if (!configParams.containsKey("tags") && reference.tags() != null) {
      configParams.put("tags", reference.tags());
    }

    String targetNode = reference.node();
    for (int i = 0; i < count; i++) {
      int newVmid = executeCall(api.getNextVmId());
      String name = description.getServerGroupName() + "-" + newVmid;
      updateStatus(
          "Cloning "
              + vmType
              + " template "
              + templateVmid
              + " to "
              + targetNode
              + " as vmid "
              + newVmid
              + " ("
              + name
              + ")");

      Map<String, String> cloneParams = new LinkedHashMap<>();
      cloneParams.put("newid", String.valueOf(newVmid));
      cloneParams.put("lxc".equals(vmType) ? "hostname" : "name", name);
      cloneParams.put("full", description.isFullClone() ? "1" : "0");
      if (description.isFullClone() && description.getStorage() != null) {
        cloneParams.put("storage", description.getStorage());
      }
      if (!targetNode.equals(templateNode)) {
        cloneParams.put("target", targetNode);
      }

      String cloneUpid;
      if ("lxc".equals(vmType)) {
        cloneUpid = executeCall(api.cloneLxc(templateNode, templateVmid, cloneParams));
      } else {
        cloneUpid = executeCall(api.cloneVm(templateNode, templateVmid, cloneParams));
      }
      pollTaskUntilDone(api, templateNode, cloneUpid);

      if (!configParams.isEmpty()) {
        updateStatus("Applying configuration to new member " + newVmid);
        String configUpid;
        if ("lxc".equals(vmType)) {
          configUpid = executeCall(api.updateLxcConfig(targetNode, newVmid, configParams));
        } else {
          configUpid = executeCall(api.updateVmConfig(targetNode, newVmid, configParams));
        }
        if (configUpid != null && configUpid.startsWith("UPID:")) {
          pollTaskUntilDone(api, targetNode, configUpid);
        }
      }

      if (description.isStartAfterClone()) {
        updateStatus("Starting new member " + newVmid);
        String startUpid;
        if ("lxc".equals(vmType)) {
          startUpid = executeCall(api.startLxc(targetNode, newVmid, Map.of()));
        } else {
          startUpid = executeCall(api.startVm(targetNode, newVmid, Map.of()));
        }
        if (startUpid != null) {
          pollTaskIgnoringFailure(api, targetNode, startUpid);
        }
      }
      updateStatus("Added member " + name + " (vmid " + newVmid + ")");
    }
  }

  private void scaleDown(ProxmoxApiService api, List<Member> members, int count) {
    // Members are sorted by vmid ascending; remove the newest (highest vmid) first.
    for (int i = 0; i < count; i++) {
      Member victim = members.get(members.size() - 1 - i);
      updateStatus(
          "Removing member " + victim.name() + " (vmid " + victim.vmid() + ") on " + victim.node());

      String stopUpid;
      if ("lxc".equals(victim.vmType())) {
        stopUpid = executeCall(api.stopLxc(victim.node(), victim.vmid(), Map.of()));
      } else {
        stopUpid = executeCall(api.stopVm(victim.node(), victim.vmid(), Map.of()));
      }
      if (stopUpid != null) {
        pollTaskIgnoringFailure(api, victim.node(), stopUpid);
      }

      String deleteUpid;
      if ("lxc".equals(victim.vmType())) {
        deleteUpid = executeCall(api.deleteLxc(victim.node(), victim.vmid()));
      } else {
        deleteUpid = executeCall(api.deleteVm(victim.node(), victim.vmid()));
      }
      if (deleteUpid != null) {
        pollTaskUntilDone(api, victim.node(), deleteUpid);
      }
      updateStatus("Removed member " + victim.name() + " (vmid " + victim.vmid() + ")");
    }
  }
}
