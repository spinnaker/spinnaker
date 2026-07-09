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
package com.netflix.spinnaker.clouddriver.proxmox.security;

import com.netflix.spectator.api.Registry;
import com.netflix.spinnaker.cats.agent.Agent;
import com.netflix.spinnaker.clouddriver.proxmox.ProxmoxProvider;
import com.netflix.spinnaker.clouddriver.proxmox.caching.agents.LxcCachingAgent;
import com.netflix.spinnaker.clouddriver.proxmox.caching.agents.NodeCachingAgent;
import com.netflix.spinnaker.clouddriver.proxmox.caching.agents.ProxmoxTemplateCachingAgent;
import com.netflix.spinnaker.clouddriver.proxmox.caching.agents.StorageCachingAgent;
import com.netflix.spinnaker.clouddriver.proxmox.caching.agents.VMCachingAgent;
import com.netflix.spinnaker.clouddriver.proxmox.names.ProxmoxTagNamer;
import com.netflix.spinnaker.credentials.CredentialsLifecycleHandler;
import java.util.Collections;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ProxmoxCredentialsLifecycleHandler
    implements CredentialsLifecycleHandler<ProxmoxNamedAccountCredentials> {

  private final ProxmoxProvider proxmoxProvider;
  private final Registry registry;
  private final ProxmoxTagNamer tagNamer;

  @Override
  public void credentialsAdded(ProxmoxNamedAccountCredentials credentials) {
    proxmoxProvider.addAgents(agentsFor(credentials));
  }

  @Override
  public void credentialsUpdated(ProxmoxNamedAccountCredentials credentials) {
    proxmoxProvider.removeAgentsForAccounts(Collections.singleton(credentials.getName()));
    proxmoxProvider.addAgents(agentsFor(credentials));
  }

  @Override
  public void credentialsDeleted(ProxmoxNamedAccountCredentials credentials) {
    proxmoxProvider.removeAgentsForAccounts(Collections.singleton(credentials.getName()));
  }

  private List<Agent> agentsFor(ProxmoxNamedAccountCredentials credentials) {
    return List.of(
        new NodeCachingAgent(credentials, registry, tagNamer),
        new VMCachingAgent(credentials, registry, tagNamer),
        new LxcCachingAgent(credentials, registry, tagNamer),
        new StorageCachingAgent(credentials, registry, tagNamer),
        new ProxmoxTemplateCachingAgent(credentials));
  }
}
