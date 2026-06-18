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

    Map<String, String> cloneParams = buildCloneParams(newVmid, targetNode, templateNode);
    String upid;
    if ("lxc".equals(vmType)) {
      upid = executeCall(api.cloneLxc(templateNode, templateVmid, cloneParams));
    } else {
      upid = executeCall(api.cloneVm(templateNode, templateVmid, cloneParams));
    }
    pollTaskUntilDone(api, templateNode, upid);

    updateStatus("Applying config overrides to '" + description.getName() + "' on " + targetNode);

    Map<String, String> configParams = buildConfigParams();
    String configUpid;
    if ("lxc".equals(vmType)) {
      configUpid = executeCall(api.updateLxcConfig(targetNode, newVmid, configParams));
    } else {
      configUpid = executeCall(api.updateVmConfig(targetNode, newVmid, configParams));
    }
    if (configUpid != null && configUpid.startsWith("UPID:")) {
      pollTaskUntilDone(api, targetNode, configUpid);
    }

    String name = description.getName();
    updateStatus("Deployed " + vmType + " '" + name + "' (vmid " + newVmid + ") on " + targetNode);

    DeploymentResult result = new DeploymentResult();
    result.getServerGroupNames().add(targetNode + ":" + name);
    result.getServerGroupNameByRegion().put(targetNode, name);
    return result;
  }

  private Map<String, String> buildCloneParams(
      int newVmid, String targetNode, String templateNode) {
    Map<String, String> params = new LinkedHashMap<>();
    params.put("newid", String.valueOf(newVmid));
    if (description.getName() != null) {
      if ("lxc".equals(description.getVmType())) {
        params.put("hostname", description.getName());
      } else {
        params.put("name", description.getName());
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

  private Map<String, String> buildConfigParams() {
    Map<String, String> params = new LinkedHashMap<>();
    params.put("memory", String.valueOf(description.getMemory()));
    params.put("cores", String.valueOf(description.getCores()));
    if (!"lxc".equals(description.getVmType())) {
      params.put("sockets", String.valueOf(description.getSockets()));
    }
    if (description.getNet0() != null) {
      params.put("net0", description.getNet0());
    }

    String tagString = buildTagString(description.getMoniker(), description.getTags());
    if (tagString != null) {
      params.put("tags", tagString);
    }

    params.putAll(description.getAdditionalOptions());
    return params;
  }

  private static String buildTagString(Moniker moniker, String existingTags) {
    Map<String, String> tags = new LinkedHashMap<>(ProxmoxTagNamer.parseTags(existingTags));

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
