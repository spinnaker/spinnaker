/*
 * Copyright 2022 Armory
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

package com.netflix.spinnaker.clouddriver.kubernetes.caching.agent;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableList;
import com.netflix.spinnaker.clouddriver.kubernetes.config.KubernetesConfigurationProperties;
import com.netflix.spinnaker.clouddriver.kubernetes.description.KubernetesResourceProperties;
import com.netflix.spinnaker.clouddriver.kubernetes.description.KubernetesSpinnakerKindMap;
import com.netflix.spinnaker.clouddriver.kubernetes.description.ResourcePropertyRegistry;
import com.netflix.spinnaker.clouddriver.kubernetes.op.handler.KubernetesDeploymentHandler;
import com.netflix.spinnaker.clouddriver.kubernetes.security.KubernetesCredentials;
import com.netflix.spinnaker.clouddriver.kubernetes.security.KubernetesNamedAccountCredentials;
import java.util.ArrayList;
import java.util.Collection;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;

public class KubernetesCachingAgentDispatcherTest {

  @Test
  public void buildAllCachingAgentsOneThread() {
    KubernetesCachingAgentDispatcher dispatcher =
        new KubernetesCachingAgentDispatcher(
            new ObjectMapper(),
            null,
            new KubernetesConfigurationProperties(),
            new KubernetesSpinnakerKindMap(new ArrayList<>()),
            null);
    KubernetesNamedAccountCredentials creds = mockCredentials(1);
    Collection<KubernetesCachingAgent> agents = dispatcher.buildAllCachingAgents(creds);

    assertNotNull(agents);
    assertEquals(1, agents.size());
  }

  @Test
  public void buildAllCachingAgentsTwoThreads() {
    KubernetesCachingAgentDispatcher dispatcher =
        new KubernetesCachingAgentDispatcher(
            new ObjectMapper(),
            null,
            new KubernetesConfigurationProperties(),
            new KubernetesSpinnakerKindMap(new ArrayList<>()),
            null);
    KubernetesNamedAccountCredentials creds = mockCredentials(2);
    Collection<KubernetesCachingAgent> agents = dispatcher.buildAllCachingAgents(creds);

    assertNotNull(agents);
    assertEquals(2, agents.size());
  }

  @Test
  public void buildAllCachingAgentsCacheDisabled() {
    KubernetesConfigurationProperties configProperties = new KubernetesConfigurationProperties();
    configProperties.getCache().setEnabled(false);
    KubernetesCachingAgentDispatcher dispatcher =
        new KubernetesCachingAgentDispatcher(
            new ObjectMapper(),
            null,
            configProperties,
            new KubernetesSpinnakerKindMap(new ArrayList<>()),
            null);
    KubernetesNamedAccountCredentials creds = mockCredentials(2);
    Collection<KubernetesCachingAgent> agents = dispatcher.buildAllCachingAgents(creds);

    assertNotNull(agents);
    assertEquals(0, agents.size());
  }

  @NotNull
  private KubernetesNamedAccountCredentials mockCredentials(int threads) {
    ResourcePropertyRegistry propertyRegistry = mock(ResourcePropertyRegistry.class);
    when(propertyRegistry.values())
        .thenReturn(
            ImmutableList.of(
                new KubernetesResourceProperties(new KubernetesDeploymentHandler(), false)));
    KubernetesCredentials credentials = mock(KubernetesCredentials.class);
    when(credentials.getResourcePropertyRegistry()).thenReturn(propertyRegistry);
    KubernetesNamedAccountCredentials namedCredentials =
        mock(KubernetesNamedAccountCredentials.class);
    when(namedCredentials.getCredentials()).thenReturn(credentials);
    when(namedCredentials.getCacheThreads()).thenReturn(threads);
    return namedCredentials;
  }
}
