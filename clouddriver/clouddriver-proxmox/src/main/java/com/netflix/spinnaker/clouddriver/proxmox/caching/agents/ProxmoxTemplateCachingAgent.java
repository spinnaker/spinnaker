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

import com.netflix.spinnaker.cats.agent.AccountAware;
import com.netflix.spinnaker.cats.agent.AgentDataType;
import com.netflix.spinnaker.cats.agent.CacheResult;
import com.netflix.spinnaker.cats.agent.CachingAgent;
import com.netflix.spinnaker.cats.agent.DefaultCacheResult;
import com.netflix.spinnaker.cats.cache.CacheData;
import com.netflix.spinnaker.cats.cache.DefaultCacheData;
import com.netflix.spinnaker.cats.provider.ProviderCache;
import com.netflix.spinnaker.clouddriver.proxmox.caching.ProxmoxCacheKeys;
import com.netflix.spinnaker.clouddriver.proxmox.caching.ProxmoxResourceType;
import com.netflix.spinnaker.clouddriver.proxmox.client.ProxmoxResponse;
import com.netflix.spinnaker.clouddriver.proxmox.model.ProxmoxLxc;
import com.netflix.spinnaker.clouddriver.proxmox.model.ProxmoxNode;
import com.netflix.spinnaker.clouddriver.proxmox.model.ProxmoxVm;
import com.netflix.spinnaker.clouddriver.proxmox.security.ProxmoxNamedAccountCredentials;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import retrofit2.Response;

/** Caches Proxmox VM and LXC templates as IMAGE resources. */
public class ProxmoxTemplateCachingAgent implements CachingAgent, AccountAware {

  private static final Logger log = LoggerFactory.getLogger(ProxmoxTemplateCachingAgent.class);

  private final ProxmoxNamedAccountCredentials credentials;

  public ProxmoxTemplateCachingAgent(ProxmoxNamedAccountCredentials credentials) {
    this.credentials = credentials;
  }

  @Override
  public String getAccountName() {
    return credentials.getName();
  }

  @Override
  public String getProviderName() {
    return com.netflix.spinnaker.clouddriver.proxmox.ProxmoxProvider.ID;
  }

  @Override
  public String getAgentType() {
    return credentials.getName() + "/" + ProxmoxTemplateCachingAgent.class.getSimpleName();
  }

  @Override
  public Collection<AgentDataType> getProvidedDataTypes() {
    return List.of(AgentDataType.Authority.AUTHORITATIVE.forType(ProxmoxResourceType.IMAGE.name()));
  }

  @Override
  public Optional<Map<String, String>> getCacheKeyPatterns() {
    return Optional.of(
        Map.of(
            ProxmoxResourceType.IMAGE.name(),
            ProxmoxCacheKeys.glob(ProxmoxResourceType.IMAGE.name(), credentials.getName())));
  }

  @Override
  public CacheResult loadData(ProviderCache providerCache) {
    List<ProxmoxNode> nodes;
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
        return emptyResult();
      }
      nodes = response.body().getData();
    } catch (IOException e) {
      log.error("Failed to fetch nodes for account {}", credentials.getName(), e);
      return emptyResult();
    }

    List<CacheData> images = new ArrayList<>();

    for (ProxmoxNode node : nodes) {
      collectVmTemplates(node.getNode(), images);
      collectLxcTemplates(node.getNode(), images);
    }

    return new DefaultCacheResult(Map.of(ProxmoxResourceType.IMAGE.name(), images));
  }

  private void collectVmTemplates(String node, List<CacheData> images) {
    try {
      Response<ProxmoxResponse<List<ProxmoxVm>>> response =
          credentials.getCredentials().getVms(node).execute();
      if (!response.isSuccessful()
          || response.body() == null
          || response.body().getData() == null) {
        log.warn(
            "Failed to fetch VMs on node {} for account {}: HTTP {}",
            node,
            credentials.getName(),
            response.code());
        return;
      }
      for (ProxmoxVm vm : response.body().getData()) {
        if (vm.getVmId() != null && Objects.equals(1, vm.getTemplate())) {
          vm.setNode(node);
          String key = ProxmoxCacheKeys.image(credentials.getName(), node, vm.getVmId());
          Map<String, Object> attrs = buildAttributes(vm.getName(), node, vm.getVmId(), "qemu");
          images.add(new DefaultCacheData(key, attrs, Collections.emptyMap()));
        }
      }
    } catch (IOException e) {
      log.error("Failed to fetch VMs on node {} for account {}", node, credentials.getName(), e);
    }
  }

  private void collectLxcTemplates(String node, List<CacheData> images) {
    try {
      Response<ProxmoxResponse<List<ProxmoxLxc>>> response =
          credentials.getCredentials().getContainers(node).execute();
      if (!response.isSuccessful()
          || response.body() == null
          || response.body().getData() == null) {
        log.warn(
            "Failed to fetch containers on node {} for account {}: HTTP {}",
            node,
            credentials.getName(),
            response.code());
        return;
      }
      for (ProxmoxLxc lxc : response.body().getData()) {
        if (lxc.getVmId() != null && Objects.equals(1, lxc.getTemplate())) {
          lxc.setNode(node);
          String key = ProxmoxCacheKeys.image(credentials.getName(), node, lxc.getVmId());
          Map<String, Object> attrs = buildAttributes(lxc.getName(), node, lxc.getVmId(), "lxc");
          images.add(new DefaultCacheData(key, attrs, Collections.emptyMap()));
        }
      }
    } catch (IOException e) {
      log.error(
          "Failed to fetch containers on node {} for account {}", node, credentials.getName(), e);
    }
  }

  private Map<String, Object> buildAttributes(String name, String node, int vmid, String vmType) {
    Map<String, Object> attrs = new HashMap<>();
    attrs.put("name", name);
    attrs.put("region", node);
    attrs.put("account", credentials.getName());
    attrs.put("vmId", vmid);
    attrs.put("vmType", vmType);
    return attrs;
  }

  private static CacheResult emptyResult() {
    return new DefaultCacheResult(Collections.emptyMap());
  }
}
