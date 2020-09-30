/*
 * Copyright 2020 Armory
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
 *
 */

package com.netflix.spinnaker.clouddriver.cloudfoundry.security;

import com.netflix.spectator.api.Registry;
import com.netflix.spinnaker.cats.agent.Agent;
import com.netflix.spinnaker.clouddriver.cloudfoundry.provider.CloudFoundryProvider;
import com.netflix.spinnaker.clouddriver.cloudfoundry.provider.agent.CloudFoundryLoadBalancerCachingAgent;
import com.netflix.spinnaker.clouddriver.cloudfoundry.provider.agent.CloudFoundryServerGroupCachingAgent;
import com.netflix.spinnaker.clouddriver.cloudfoundry.provider.agent.CloudFoundrySpaceCachingAgent;
import com.netflix.spinnaker.credentials.CredentialsLifecycleHandler;
import java.util.Collections;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class CloudFoundryCredentialsLifecycleHandler
    implements CredentialsLifecycleHandler<CloudFoundryCredentials> {
  private static final Logger log =
      LoggerFactory.getLogger(CloudFoundryCredentialsLifecycleHandler.class);
  private final CloudFoundryProvider provider;
  private final Registry registry;

  @Override
  public void credentialsAdded(CloudFoundryCredentials credentials) {
    log.info("Adding agents for new account {}", credentials.getName());
    provider.addAgents(agentsForCredentials(credentials));
  }

  @Override
  public void credentialsUpdated(CloudFoundryCredentials credentials) {
    log.info("Refreshing agents for updated account {}", credentials.getName());
    provider.removeAgentsForAccounts(Collections.singleton(credentials.getName()));
    provider.addAgents(agentsForCredentials(credentials));
  }

  @Override
  public void credentialsDeleted(CloudFoundryCredentials credentials) {
    log.info("Removing agents for deleted account {}", credentials.getName());
    provider.removeAgentsForAccounts(Collections.singleton(credentials.getName()));
  }

  private List<Agent> agentsForCredentials(CloudFoundryCredentials credentials) {
    return List.of(
        new CloudFoundryServerGroupCachingAgent(credentials, registry),
        new CloudFoundryLoadBalancerCachingAgent(credentials, registry),
        new CloudFoundrySpaceCachingAgent(credentials, registry));
  }
}
