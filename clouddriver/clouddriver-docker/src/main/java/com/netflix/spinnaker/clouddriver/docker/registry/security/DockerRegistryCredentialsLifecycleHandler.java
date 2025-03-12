/*
 * Copyright 2021 Armory
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

package com.netflix.spinnaker.clouddriver.docker.registry.security;

import com.netflix.spinnaker.cats.agent.Agent;
import com.netflix.spinnaker.clouddriver.docker.registry.DockerRegistryCloudProvider;
import com.netflix.spinnaker.clouddriver.docker.registry.provider.DockerRegistryProvider;
import com.netflix.spinnaker.clouddriver.docker.registry.provider.agent.DockerRegistryImageCachingAgent;
import com.netflix.spinnaker.credentials.CredentialsLifecycleHandler;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class DockerRegistryCredentialsLifecycleHandler
    implements CredentialsLifecycleHandler<DockerRegistryNamedAccountCredentials> {

  private final DockerRegistryProvider provider;
  private final DockerRegistryCloudProvider cloudProvider;

  @Override
  public void credentialsAdded(DockerRegistryNamedAccountCredentials credentials) {
    log.info("Adding agents for docker account {}", credentials.getName());
    provider.addAgents(agentsForCredentials(credentials));
  }

  @Override
  public void credentialsUpdated(DockerRegistryNamedAccountCredentials credentials) {
    log.info("Updating agents for docker account {}", credentials.getName());
    provider.removeAgentsForAccounts(List.of(credentials.getName()));
    provider.addAgents(agentsForCredentials(credentials));
  }

  @Override
  public void credentialsDeleted(DockerRegistryNamedAccountCredentials credentials) {
    log.info("Removing agents for docker account {}", credentials.getName());
    provider.removeAgentsForAccounts(List.of(credentials.getName()));
  }

  private List<Agent> agentsForCredentials(DockerRegistryNamedAccountCredentials credentials) {
    List<Agent> agents = new ArrayList<>();

    for (int i = 0; i < credentials.getCacheThreads(); i++) {
      agents.add(
          new DockerRegistryImageCachingAgent(
              cloudProvider,
              credentials.getName(),
              credentials.getCredentials(),
              i,
              credentials.getCacheThreads(),
              credentials.getCacheIntervalSeconds(),
              credentials.getRegistry()));
    }
    return agents;
  }
}
