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

package com.netflix.spinnaker.clouddriver.kubernetes.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.netflix.spinnaker.clouddriver.kubernetes.caching.KubernetesProvider;
import com.netflix.spinnaker.clouddriver.kubernetes.caching.agent.KubernetesCachingAgent;
import com.netflix.spinnaker.clouddriver.kubernetes.caching.agent.KubernetesCachingAgentDispatcher;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.platform.runner.JUnitPlatform;
import org.junit.runner.RunWith;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;

@RunWith(JUnitPlatform.class)
public class KubernetesCredentialsLifecycleHandlerTest {

  @Test
  public void testAddCredentials() {
    KubernetesProvider provider = new KubernetesProvider();
    KubernetesCachingAgentDispatcher cachingAgentDispatcher =
        mock(KubernetesCachingAgentDispatcher.class);
    when(cachingAgentDispatcher.buildAllCachingAgents(ArgumentMatchers.any()))
        .thenAnswer(d -> Collections.singleton(mock(KubernetesCachingAgent.class)));
    KubernetesCredentialsLifecycleHandler handler =
        new KubernetesCredentialsLifecycleHandler(provider, cachingAgentDispatcher);

    // Check we start with no agents
    assertThat(provider.getAgents()).isEmpty();

    KubernetesNamedAccountCredentials namedCredentials =
        Mockito.mock(KubernetesNamedAccountCredentials.class);
    when(namedCredentials.getCredentials()).thenReturn(mock(KubernetesCredentials.class));

    handler.credentialsAdded(namedCredentials);
    // We should have added an agent
    assertThat(provider.getAgents()).hasSize(1);

    handler.credentialsAdded(namedCredentials);
    // We should have yet another one
    assertThat(provider.getAgents()).hasSize(2);
  }

  @Test
  public void testRemoveCredentials() {
    String ACCOUNT1 = "account1";
    String ACCOUNT2 = "account2";
    KubernetesProvider provider = new KubernetesProvider();

    KubernetesCachingAgent agent1 = mock(KubernetesCachingAgent.class);
    when(agent1.handlesAccount(ACCOUNT1)).thenReturn(true);

    KubernetesCachingAgent agent2 = mock(KubernetesCachingAgent.class);
    when(agent1.handlesAccount(ACCOUNT2)).thenReturn(true);

    provider.addAgents(List.of(agent1, agent2));

    KubernetesCredentialsLifecycleHandler handler =
        new KubernetesCredentialsLifecycleHandler(provider, null);

    assertThat(provider.getAgents()).hasSize(2);

    KubernetesNamedAccountCredentials cred1 = mock(KubernetesNamedAccountCredentials.class);
    when(cred1.getName()).thenReturn(ACCOUNT1);
    handler.credentialsDeleted(cred1);

    // We removed account1 so only agent2 should remain
    assertThat(provider.getAgents()).hasSize(1);
    assertThat(provider.getAgents()).contains(agent2);
  }
}
