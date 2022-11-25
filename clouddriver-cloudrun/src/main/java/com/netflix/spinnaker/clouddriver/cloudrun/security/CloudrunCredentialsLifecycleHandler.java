/*
 * Copyright 2022 OpsMx
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

package com.netflix.spinnaker.clouddriver.cloudrun.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spectator.api.Registry;
import com.netflix.spinnaker.clouddriver.cloudrun.provider.CloudrunProvider;
import com.netflix.spinnaker.clouddriver.cloudrun.provider.agent.CloudrunServerGroupCachingAgent;
import com.netflix.spinnaker.credentials.CredentialsLifecycleHandler;
import java.util.Collections;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class CloudrunCredentialsLifecycleHandler
    implements CredentialsLifecycleHandler<CloudrunNamedAccountCredentials> {

  private final CloudrunProvider cloudrunCloudProvider;
  private final ObjectMapper objectMapper;
  private final Registry registry;

  @Override
  public void credentialsAdded(CloudrunNamedAccountCredentials credentials) {
    addAgentFor(credentials);
  }

  @Override
  public void credentialsUpdated(CloudrunNamedAccountCredentials credentials) {
    cloudrunCloudProvider.removeAgentsForAccounts(Collections.singleton(credentials.getName()));
    addAgentFor(credentials);
  }

  @Override
  public void credentialsDeleted(CloudrunNamedAccountCredentials credentials) {
    cloudrunCloudProvider.removeAgentsForAccounts(Collections.singleton(credentials.getName()));
  }

  private void addAgentFor(CloudrunNamedAccountCredentials credentials) {
    cloudrunCloudProvider.addAgents(
        List.of(
            new CloudrunServerGroupCachingAgent(
                credentials.getName(), credentials, objectMapper, registry)));
  }
}
