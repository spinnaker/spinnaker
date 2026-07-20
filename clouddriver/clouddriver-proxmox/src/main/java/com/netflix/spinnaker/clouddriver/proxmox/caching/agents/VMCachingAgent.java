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

package com.netflix.spinnaker.clouddriver.proxmox.caching.agents;

import com.netflix.spectator.api.Registry;
import com.netflix.spinnaker.cats.agent.AgentDataType;
import com.netflix.spinnaker.clouddriver.proxmox.caching.ProxmoxCacheKeys;
import com.netflix.spinnaker.clouddriver.proxmox.caching.ProxmoxResourceType;
import com.netflix.spinnaker.clouddriver.proxmox.model.ProxmoxNode;
import com.netflix.spinnaker.clouddriver.proxmox.model.ProxmoxVm;
import com.netflix.spinnaker.clouddriver.proxmox.names.ProxmoxTagNamer;
import com.netflix.spinnaker.clouddriver.proxmox.security.ProxmoxNamedAccountCredentials;
import java.io.IOException;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import retrofit2.Response;

public class VMCachingAgent extends AbstractProxmoxCachingAgent {
  /** QEMU disk devices, including EFI and TPM state volumes and detached (unused) disks. */
  private static final java.util.regex.Pattern QEMU_DISK_KEYS =
      java.util.regex.Pattern.compile("^(scsi|sata|virtio|ide|efidisk|tpmstate|unused)\\d+$");

  public VMCachingAgent(
      ProxmoxNamedAccountCredentials credentials, Registry registry, ProxmoxTagNamer tagNamer) {
    super(credentials, registry, tagNamer);
  }

  @Override
  public Collection<AgentDataType> getProvidedDataTypes() {
    return List.of(
        AgentDataType.Authority.AUTHORITATIVE.forType(ProxmoxResourceType.VM.name()),
        AgentDataType.Authority.AUTHORITATIVE.forType(ProxmoxResourceType.APPLICATION.name()),
        AgentDataType.Authority.AUTHORITATIVE.forType(ProxmoxResourceType.CLUSTER.name()));
  }

  @Override
  Map<String, ProxmoxVm> getItems() {
    try {
      List<ProxmoxNode> nodes = fetchNodes();
      Map<String, ProxmoxVm> result = new HashMap<>();
      for (ProxmoxNode node : nodes) {
        try {
          Response<
                  com.netflix.spinnaker.clouddriver.proxmox.client.ProxmoxResponse<List<ProxmoxVm>>>
              response = credentials.getCredentials().getVms(node.getNode()).execute();
          if (!response.isSuccessful()
              || response.body() == null
              || response.body().getData() == null) {
            log.warn(
                "Failed to fetch VMs on node {} for account {}: HTTP {}",
                node.getNode(),
                credentials.getName(),
                response.code());
            continue;
          }
          for (ProxmoxVm vm : response.body().getData()) {
            if (vm.getVmId() != null && !Objects.equals(1, vm.getTemplate())) {
              vm.setNode(node.getNode());
              Map<String, Object> config =
                  fetchConfig(
                      credentials.getCredentials().getVmConfig(node.getNode(), vm.getVmId()),
                      node.getNode(),
                      vm.getVmId());
              if (config != null) {
                mergeConfig(vm, config);
                vm.setDisks(extractDiskEntries(config, QEMU_DISK_KEYS));
              }
              result.put(
                  ProxmoxCacheKeys.vm(credentials.getName(), node.getNode(), vm.getVmId()), vm);
            }
          }
        } catch (IOException e) {
          log.error(
              "Failed to fetch VMs on node {} for account {}",
              node.getNode(),
              credentials.getName(),
              e);
        }
      }
      return result;
    } catch (IOException e) {
      log.error("Failed to fetch nodes for account {}", credentials.getName(), e);
      return Map.of();
    }
  }
}
