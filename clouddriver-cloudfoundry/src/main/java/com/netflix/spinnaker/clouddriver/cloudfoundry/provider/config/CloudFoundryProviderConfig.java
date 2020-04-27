/*
 * Copyright 2018 Pivotal, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.clouddriver.cloudfoundry.provider.config;

import com.netflix.spectator.api.Registry;
import com.netflix.spinnaker.clouddriver.cloudfoundry.provider.CloudFoundryProvider;
import com.netflix.spinnaker.clouddriver.cloudfoundry.provider.agent.CloudFoundryLoadBalancerCachingAgent;
import com.netflix.spinnaker.clouddriver.cloudfoundry.provider.agent.CloudFoundryServerGroupCachingAgent;
import com.netflix.spinnaker.clouddriver.cloudfoundry.provider.agent.CloudFoundrySpaceCachingAgent;
import com.netflix.spinnaker.clouddriver.cloudfoundry.security.CloudFoundryCredentials;
import com.netflix.spinnaker.clouddriver.security.AccountCredentialsRepository;
import com.netflix.spinnaker.clouddriver.security.ProviderUtils;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.DependsOn;

@Configuration
public class CloudFoundryProviderConfig {

  @Bean
  @DependsOn("cloudFoundryAccountCredentials")
  public CloudFoundryProvider cloudFoundryProvider(
      AccountCredentialsRepository accountCredentialsRepository, Registry registry) {
    CloudFoundryProvider provider =
        new CloudFoundryProvider(Collections.newSetFromMap(new ConcurrentHashMap<>()));
    synchronizeCloudFoundryProvider(provider, accountCredentialsRepository, registry);
    return provider;
  }

  private void synchronizeCloudFoundryProvider(
      CloudFoundryProvider cloudFoundryProvider,
      AccountCredentialsRepository accountCredentialsRepository,
      Registry registry) {
    Set<String> scheduledAccounts = ProviderUtils.getScheduledAccounts(cloudFoundryProvider);
    Set<CloudFoundryCredentials> allAccounts =
        ProviderUtils.buildThreadSafeSetOfAccounts(
            accountCredentialsRepository, CloudFoundryCredentials.class);
    allAccounts.forEach(
        credentials -> {
          if (!scheduledAccounts.contains(credentials.getName())) {
            cloudFoundryProvider
                .getAgents()
                .add(new CloudFoundryServerGroupCachingAgent(credentials, registry));
            cloudFoundryProvider
                .getAgents()
                .add(new CloudFoundryLoadBalancerCachingAgent(credentials, registry));
            cloudFoundryProvider
                .getAgents()
                .add(new CloudFoundrySpaceCachingAgent(credentials, registry));
          }
        });
  }
}
