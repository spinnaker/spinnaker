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

/** Creates a new QEMU VM or LXC container on a Proxmox node. */
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
    String node = description.getNode();
    String vmType = description.getVmType();

    updateStatus("Creating Proxmox " + vmType + " '" + description.getName() + "' on node " + node);

    Map<String, String> params = buildParams();
    String upid;
    if ("lxc".equals(vmType)) {
      upid = executeCall(api.createLxc(node, params));
    } else {
      upid = executeCall(api.createVm(node, params));
    }
    pollTaskUntilDone(api, node, upid);

    String name = description.getName();
    updateStatus("Created " + vmType + " '" + name + "' on " + node);

    DeploymentResult result = new DeploymentResult();
    result.getServerGroupNames().add(node + ":" + name);
    result.getServerGroupNameByRegion().put(node, name);
    return result;
  }

  private Map<String, String> buildParams() {
    Map<String, String> params = new LinkedHashMap<>();

    if (description.getVmid() != null) {
      params.put("vmid", String.valueOf(description.getVmid()));
    }

    if ("lxc".equals(description.getVmType())) {
      params.put("hostname", description.getName());
      params.put("ostemplate", description.getOsTemplate());
      params.put("rootfs", description.getStorage() + ":" + description.getDiskSize());
      params.put("memory", String.valueOf(description.getMemory()));
      params.put("cores", String.valueOf(description.getCores()));
      params.put("net0", description.getNet0());
    } else {
      params.put("name", description.getName());
      params.put("memory", String.valueOf(description.getMemory()));
      params.put("cores", String.valueOf(description.getCores()));
      params.put("sockets", String.valueOf(description.getSockets()));
      params.put(
          "scsi0",
          description.getStorage()
              + ":"
              + description.getDiskSize()
              + ",format="
              + description.getDiskFormat());
      params.put("net0", description.getNet0());
      params.put("scsihw", description.getScsiHw());
      if (description.getCdrom() != null) {
        params.put("cdrom", description.getCdrom());
      }
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
