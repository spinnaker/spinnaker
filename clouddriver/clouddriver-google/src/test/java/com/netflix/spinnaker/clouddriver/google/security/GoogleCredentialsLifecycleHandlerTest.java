/*
 * Copyright 2026 Harness, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
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

package com.netflix.spinnaker.clouddriver.google.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.api.services.compute.Compute;
import com.netflix.spectator.api.DefaultRegistry;
import com.netflix.spinnaker.clouddriver.google.compute.GoogleComputeApiFactory;
import com.netflix.spinnaker.clouddriver.google.config.GoogleConfigurationProperties;
import com.netflix.spinnaker.clouddriver.google.provider.GoogleInfrastructureProvider;
import com.netflix.spinnaker.clouddriver.google.provider.agent.GoogleExternalHttpLoadBalancerCachingAgent;
import com.netflix.spinnaker.clouddriver.google.provider.agent.GoogleRegionalExternalNetworkLoadBalancerCachingAgent;
import com.netflix.spinnaker.kork.client.ServiceClientProvider;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;

class GoogleCredentialsLifecycleHandlerTest {
  @Test
  void credentialsAdded_registersRegionalExternalLoadBalancerAgentsOncePerRegion() {
    GoogleInfrastructureProvider provider = mock(GoogleInfrastructureProvider.class);
    GoogleCredentialsLifecycleHandler handler = createHandler(provider);
    GoogleNamedAccountCredentials credentials = createCredentials();

    handler.credentialsAdded(credentials);

    assertRegionalExternalAgentsRegistered(provider);
  }

  @Test
  void credentialsUpdated_removesExistingAccountAgentsBeforeRegisteringRegionalExternalAgents() {
    GoogleInfrastructureProvider provider = mock(GoogleInfrastructureProvider.class);
    GoogleCredentialsLifecycleHandler handler = createHandler(provider);
    GoogleNamedAccountCredentials credentials = createCredentials();

    handler.credentialsUpdated(credentials);

    InOrder inOrder = inOrder(provider);
    inOrder.verify(provider).removeAgentsForAccounts(Set.of("auto"));
    inOrder.verify(provider, times(2)).addAgents(anyCollection());
    assertRegionalExternalAgentsRegistered(provider);
  }

  @Test
  void credentialsDeleted_removesExistingAccountAgentsWithoutRegisteringNewAgents() {
    GoogleInfrastructureProvider provider = mock(GoogleInfrastructureProvider.class);
    GoogleCredentialsLifecycleHandler handler = createHandler(provider);
    GoogleNamedAccountCredentials credentials = createCredentials();

    handler.credentialsDeleted(credentials);

    verify(provider).removeAgentsForAccounts(Set.of("auto"));
    verify(provider, never()).addAgents(anyCollection());
  }

  private static GoogleCredentialsLifecycleHandler createHandler(
      GoogleInfrastructureProvider provider) {
    return new GoogleCredentialsLifecycleHandler(
        provider,
        new GoogleConfigurationProperties(),
        mock(GoogleComputeApiFactory.class),
        new ObjectMapper(),
        new DefaultRegistry(),
        "clouddriver",
        mock(ServiceClientProvider.class));
  }

  private static GoogleNamedAccountCredentials createCredentials() {
    return new GoogleNamedAccountCredentials.Builder()
        .name("auto")
        .project("my-project")
        .compute(mock(Compute.class))
        .credentials(mock(GoogleCredentials.class))
        .regionToZonesMap(
            Map.of(
                "us-central1", List.of("us-central1-a"),
                "europe-west1", List.of("europe-west1-b")))
        .build();
  }

  private static void assertRegionalExternalAgentsRegistered(
      GoogleInfrastructureProvider provider) {
    ArgumentCaptor<Collection> agentsCaptor = ArgumentCaptor.forClass(Collection.class);
    verify(provider, times(2)).addAgents(agentsCaptor.capture());
    Collection<?> cachingAgents = agentsCaptor.getAllValues().get(0);
    assertThat(cachingAgents.stream())
        .filteredOn(agent -> agent instanceof GoogleExternalHttpLoadBalancerCachingAgent)
        .hasSize(2);
    assertThat(cachingAgents.stream())
        .filteredOn(agent -> agent instanceof GoogleRegionalExternalNetworkLoadBalancerCachingAgent)
        .hasSize(2);
  }
}
