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
import com.netflix.spinnaker.clouddriver.proxmox.caching.ProxmoxResourceType;
import com.netflix.spinnaker.clouddriver.proxmox.client.ProxmoxResponse;
import com.netflix.spinnaker.clouddriver.proxmox.model.ProxmoxNode;
import com.netflix.spinnaker.clouddriver.proxmox.security.ProxmoxNamedAccountCredentials;
import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import retrofit2.Response;

@Component
public class NodeCachingAgent extends AbstractProxmoxCachingAgent {
  private static final Logger log = LoggerFactory.getLogger(NodeCachingAgent.class);

  public NodeCachingAgent(ProxmoxNamedAccountCredentials credentials, Registry registry) {
    super(credentials, registry);
  }

  @Override
  public Collection<AgentDataType> getProvidedDataTypes() {
    return List.of(AgentDataType.Authority.AUTHORITATIVE.forType(ProxmoxResourceType.NODE.name()));
  }

  @Override
  Map<String, ProxmoxNode> getItems() {
    try {
      Response<ProxmoxResponse<List<ProxmoxNode>>> response =
          credentials.getCredentials().getNodes().execute();
      if (!response.isSuccessful()
          || response.body() == null
          || response.body().getData() == null) {
        log.warn(
            "Failed to fetch nodes for account {}: HTTP {}",
            credentials.getName(),
            response.code());
        return Map.of();
      }
      return response.body().getData().stream()
          .filter(node -> node.getNode() != null)
          .collect(Collectors.toMap(node -> "node/" + node.getNode(), node -> node));
    } catch (IOException e) {
      log.error("Failed to fetch Proxmox nodes for account {}", credentials.getName(), e);
      return Map.of();
    }
  }
}
