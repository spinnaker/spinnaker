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

import com.fasterxml.jackson.databind.JsonNode;
import com.netflix.spectator.api.Registry;
import com.netflix.spinnaker.cats.agent.AgentDataType;
import com.netflix.spinnaker.clouddriver.proxmox.caching.ProxmoxResourceType;
import com.netflix.spinnaker.clouddriver.proxmox.security.ProxmoxNamedAccountCredentials;
import it.corsinvest.proxmoxve.api.PveClient;
import java.util.Collection;
import java.util.List;
import java.util.Map;

public class NodeCachingAgent extends AbstractProxmoxCachingAgent {
  public NodeCachingAgent(ProxmoxNamedAccountCredentials credentials, Registry registry) {
    super(credentials, registry);
  }

  @Override
  public Collection<AgentDataType> getProvidedDataTypes() {
    return List.of(AgentDataType.Authority.AUTHORITATIVE.forType(ProxmoxResourceType.NODE.name()));
  }

  @Override
  Map<String, ?> getItems() {
    var client = new PveClient("pve.example.com", 8006);

    // Get all VMs in cluster
    client.getNodes().get(0).getQemu().createVm(100);
    var resources = client.getCluster().getResources().resources().getData();
    for (JsonNode resource : resources) {
      if ("qemu".equals(resource.get("type").asText())) {
        System.out.printf(
            "VM %d: %s on %s - %s%n",
            resource.get("vmid").asInt(),
            resource.get("name").asText(),
            resource.get("node").asText(),
            resource.get("status").asText());
      }
    }

    return Map.of();
  }
}
