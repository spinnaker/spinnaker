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
import com.netflix.spinnaker.clouddriver.proxmox.client.ProxmoxResponse;
import com.netflix.spinnaker.clouddriver.proxmox.model.ProxmoxNode;
import com.netflix.spinnaker.clouddriver.proxmox.model.ProxmoxStorage;
import com.netflix.spinnaker.clouddriver.proxmox.security.ProxmoxNamedAccountCredentials;
import java.io.IOException;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import retrofit2.Response;

public class StorageCachingAgent extends AbstractProxmoxCachingAgent {
  public StorageCachingAgent(ProxmoxNamedAccountCredentials credentials, Registry registry) {
    super(credentials, registry);
  }

  @Override
  public Collection<AgentDataType> getProvidedDataTypes() {
    return List.of(
        AgentDataType.Authority.AUTHORITATIVE.forType(ProxmoxResourceType.STORAGE.name()));
  }

  @Override
  Map<String, ProxmoxStorage> getItems() {
    try {
      List<ProxmoxNode> nodes = fetchNodes();
      Map<String, ProxmoxStorage> result = new HashMap<>();
      for (ProxmoxNode node : nodes) {
        try {
          Response<ProxmoxResponse<List<ProxmoxStorage>>> response =
              credentials.getCredentials().getStorage(node.getNode()).execute();
          if (!response.isSuccessful()
              || response.body() == null
              || response.body().getData() == null) {
            log.warn(
                "Failed to fetch storage on node {} for account {}: HTTP {}",
                node.getNode(),
                credentials.getName(),
                response.code());
            continue;
          }
          for (ProxmoxStorage storage : response.body().getData()) {
            if (storage.getStorage() != null) {
              storage.setNode(node.getNode());
              result.put(
                  ProxmoxCacheKeys.storage(
                      credentials.getName(), node.getNode(), storage.getStorage()),
                  storage);
            }
          }
        } catch (IOException e) {
          log.error(
              "Failed to fetch storage on node {} for account {}",
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
