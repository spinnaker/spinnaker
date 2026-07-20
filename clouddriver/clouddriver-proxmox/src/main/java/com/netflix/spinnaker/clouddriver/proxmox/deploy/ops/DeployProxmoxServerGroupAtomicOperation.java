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

import com.netflix.spinnaker.clouddriver.deploy.DeploymentResult;
import com.netflix.spinnaker.clouddriver.proxmox.client.ProxmoxApiService;
import com.netflix.spinnaker.clouddriver.proxmox.deploy.description.ProxmoxDeployDescription;
import com.netflix.spinnaker.clouddriver.proxmox.names.ProxmoxTagNamer;
import com.netflix.spinnaker.moniker.Moniker;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** Clones a Proxmox template into a new QEMU VM or LXC container. */
public class DeployProxmoxServerGroupAtomicOperation
    extends AbstractProxmoxAtomicOperation<DeploymentResult> {

  private static final String PHASE = "DEPLOY_PROXMOX_SERVER_GROUP";

  private final ProxmoxDeployDescription description;

  public DeployProxmoxServerGroupAtomicOperation(ProxmoxDeployDescription description) {
    super(PHASE);
    this.description = description;
  }

  @Override
  public DeploymentResult operate(List priorOutputs) {
    ProxmoxApiService api = description.getApiService();
    String targetNode = description.getNode();
    String templateNode =
        description.getTemplateNode() != null ? description.getTemplateNode() : targetNode;
    int templateVmid = description.getTemplateVmid();
    String vmType = description.getVmType();

    // Version the server group Spinnaker-style: each deploy into a cluster creates a new
    // cluster-vNNN server group so deck shows cluster → versions → instances.
    String clusterName =
        description.getMoniker() != null && description.getMoniker().getCluster() != null
            ? description.getMoniker().getCluster()
            : description.getName();
    int sequence = ProxmoxServerGroupMembers.nextSequence(api, clusterName);
    String serverGroupName = String.format("%s-v%03d", clusterName, sequence);
    if (description.getMoniker() != null) {
      description.getMoniker().setCluster(clusterName);
      description.getMoniker().setSequence(sequence);
    }
    updateStatus("Deploying server group " + serverGroupName + " (cluster " + clusterName + ")");

    int newVmid =
        description.getVmid() != null ? description.getVmid() : executeCall(api.getNextVmId());

    updateStatus(
        "Cloning "
            + vmType
            + " template "
            + templateVmid
            + " from "
            + templateNode
            + " to "
            + targetNode
            + " as vmid "
            + newVmid);

    Map<String, String> cloneParams =
        buildCloneParams(serverGroupName, newVmid, targetNode, templateNode);
    String upid;
    if ("lxc".equals(vmType)) {
      upid = executeCall(api.cloneLxc(templateNode, templateVmid, cloneParams));
    } else {
      upid = executeCall(api.cloneVm(templateNode, templateVmid, cloneParams));
    }
    pollTaskUntilDone(api, templateNode, upid);

    updateStatus("Applying config overrides to '" + serverGroupName + "' on " + targetNode);

    Map<String, String> configParams = buildConfigParams(serverGroupName);
    String configUpid;
    if ("lxc".equals(vmType)) {
      configUpid = executeCall(api.updateLxcConfig(targetNode, newVmid, configParams));
    } else {
      configUpid = executeCall(api.updateVmConfig(targetNode, newVmid, configParams));
    }
    if (configUpid != null && configUpid.startsWith("UPID:")) {
      pollTaskUntilDone(api, targetNode, configUpid);
    }

    if (!"lxc".equals(vmType) && description.getDiskSize() != null) {
      updateStatus(
          "Resizing disk " + description.getDiskDevice() + " to " + description.getDiskSize());
      Map<String, String> resizeParams = new LinkedHashMap<>();
      resizeParams.put("disk", description.getDiskDevice());
      resizeParams.put("size", description.getDiskSize());
      String resizeUpid = executeCall(api.resizeVmDisk(targetNode, newVmid, resizeParams));
      if (resizeUpid != null && resizeUpid.startsWith("UPID:")) {
        pollTaskUntilDone(api, targetNode, resizeUpid);
      }
    }

    if (!"lxc".equals(vmType) && description.isRegenerateCloudInit()) {
      updateStatus("Regenerating cloud-init drive");
      executeCall(api.regenerateCloudInit(targetNode, newVmid, Map.of()));
    }

    updateStatus(
        "Deployed "
            + vmType
            + " '"
            + serverGroupName
            + "' (vmid "
            + newVmid
            + ") on "
            + targetNode);

    DeploymentResult result = new DeploymentResult();
    result.getServerGroupNames().add(targetNode + ":" + serverGroupName);
    result.getServerGroupNameByRegion().put(targetNode, serverGroupName);
    return result;
  }

  private Map<String, String> buildCloneParams(
      String serverGroupName, int newVmid, String targetNode, String templateNode) {
    Map<String, String> params = new LinkedHashMap<>();
    params.put("newid", String.valueOf(newVmid));
    if (serverGroupName != null) {
      if ("lxc".equals(description.getVmType())) {
        params.put("hostname", serverGroupName);
      } else {
        params.put("name", serverGroupName);
      }
    }
    params.put("full", description.isFullClone() ? "1" : "0");
    if (description.getStorage() != null) {
      params.put("storage", description.getStorage());
    }
    if (!targetNode.equals(templateNode)) {
      params.put("target", targetNode);
    }
    return params;
  }

  private Map<String, String> buildConfigParams(String serverGroupName) {
    Map<String, String> params = new LinkedHashMap<>();
    params.put("memory", String.valueOf(description.getMemory()));
    params.put("cores", String.valueOf(description.getCores()));
    if (!"lxc".equals(description.getVmType())) {
      params.put("sockets", String.valueOf(description.getSockets()));
    }
    if (description.getNet0() != null) {
      params.put("net0", description.getNet0());
    }
    if (!"lxc".equals(description.getVmType()) && description.getIpconfig0() != null) {
      params.put("ipconfig0", description.getIpconfig0());
    }
    if (!"lxc".equals(description.getVmType()) && description.getBios() != null) {
      params.put("bios", description.getBios());
      if ("ovmf".equals(description.getBios())) {
        String efiPool =
            description.getEfiStorage() != null
                ? description.getEfiStorage()
                : description.getStorage();
        params.put("efidisk0", efiPool + ":1,efitype=4m,pre-enrolled-keys=0");
      }
    }

    String tagString =
        buildTagString(
            description.getMoniker(),
            description.getTags(),
            serverGroupName,
            description.getTemplateVmid());
    if (tagString != null) {
      params.put("tags", tagString);
    }

    params.putAll(description.getAdditionalOptions());
    return params;
  }

  private static String buildTagString(
      Moniker moniker, String existingTags, String serverGroupName, int templateVmid) {
    Map<String, String> tags = new LinkedHashMap<>(ProxmoxTagNamer.parseTags(existingTags));

    // Group-membership and provenance tags: scaling operations resolve members by the
    // server-group tag and clone additional instances from the recorded template.
    if (serverGroupName != null) {
      tags.put(ProxmoxTagNamer.SERVER_GROUP_TAG, serverGroupName);
    }
    if (templateVmid > 0) {
      tags.put(ProxmoxTagNamer.TEMPLATE_TAG, String.valueOf(templateVmid));
    }

    if (moniker != null) {
      if (moniker.getApp() != null) tags.put(ProxmoxTagNamer.APP_TAG, moniker.getApp());
      if (moniker.getCluster() != null) tags.put(ProxmoxTagNamer.CLUSTER_TAG, moniker.getCluster());
      if (moniker.getStack() != null) tags.put(ProxmoxTagNamer.STACK_TAG, moniker.getStack());
      if (moniker.getDetail() != null) tags.put(ProxmoxTagNamer.DETAIL_TAG, moniker.getDetail());
      if (moniker.getSequence() != null)
        tags.put(ProxmoxTagNamer.SEQUENCE_TAG, String.valueOf(moniker.getSequence()));
    }

    if (tags.isEmpty()) return null;
    return tags.entrySet().stream()
        .map(e -> e.getKey() + "+" + e.getValue())
        .collect(Collectors.joining(";"));
  }
}
