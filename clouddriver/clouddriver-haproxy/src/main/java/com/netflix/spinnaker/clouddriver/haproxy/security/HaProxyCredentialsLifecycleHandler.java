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
package com.netflix.spinnaker.clouddriver.haproxy.security;

import com.netflix.spectator.api.Registry;
import com.netflix.spinnaker.cats.agent.Agent;
import com.netflix.spinnaker.clouddriver.haproxy.HaProxyProvider;
import com.netflix.spinnaker.clouddriver.haproxy.caching.agents.BackendCachingAgent;
import com.netflix.spinnaker.clouddriver.haproxy.caching.agents.FrontendCachingAgent;
import com.netflix.spinnaker.clouddriver.haproxy.names.HaProxyMetadataNamer;
import com.netflix.spinnaker.credentials.CredentialsLifecycleHandler;
import java.util.Collections;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class HaProxyCredentialsLifecycleHandler
    implements CredentialsLifecycleHandler<HaProxyNamedAccountCredentials> {

  private final HaProxyProvider haProxyProvider;
  private final Registry registry;
  private final HaProxyMetadataNamer namer;

  @Override
  public void credentialsAdded(HaProxyNamedAccountCredentials credentials) {
    haProxyProvider.addAgents(agentsFor(credentials));
  }

  @Override
  public void credentialsUpdated(HaProxyNamedAccountCredentials credentials) {
    haProxyProvider.removeAgentsForAccounts(Collections.singleton(credentials.getName()));
    haProxyProvider.addAgents(agentsFor(credentials));
  }

  @Override
  public void credentialsDeleted(HaProxyNamedAccountCredentials credentials) {
    haProxyProvider.removeAgentsForAccounts(Collections.singleton(credentials.getName()));
  }

  private List<Agent> agentsFor(HaProxyNamedAccountCredentials credentials) {
    return List.of(
        new FrontendCachingAgent(credentials, registry, namer),
        new BackendCachingAgent(credentials, registry, namer));
  }
}
