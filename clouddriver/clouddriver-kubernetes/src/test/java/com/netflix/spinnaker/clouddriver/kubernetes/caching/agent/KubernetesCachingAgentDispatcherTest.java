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
import com.netflix.spectator.api.NoopRegistry;
import com.netflix.spinnaker.cats.agent.NoOpStartupConcurrencyControl;
import com.netflix.spinnaker.clouddriver.kubernetes.caching.agent.streaming.KubernetesStreamingCachingAgent;
import com.netflix.spinnaker.clouddriver.kubernetes.config.KubernetesConfigurationProperties;
import com.netflix.spinnaker.clouddriver.kubernetes.config.KubernetesStreamingCachingProperties;
import com.netflix.spinnaker.clouddriver.kubernetes.description.KubernetesResourceProperties;
import com.netflix.spinnaker.clouddriver.kubernetes.description.KubernetesSpinnakerKindMap;
import com.netflix.spinnaker.clouddriver.kubernetes.description.ResourcePropertyRegistry;
import com.netflix.spinnaker.clouddriver.kubernetes.op.handler.KubernetesDeploymentHandler;
import com.netflix.spinnaker.clouddriver.kubernetes.security.KubernetesCredentials;
import com.netflix.spinnaker.clouddriver.kubernetes.security.KubernetesNamedAccountCredentials;
import java.util.ArrayList;
import java.util.Collection;
import java.util.stream.Stream;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

public class KubernetesCachingAgentDispatcherTest {

  @ParameterizedTest
  @MethodSource("agentTypes")
  public void buildAllCachingAgentsOneThread(boolean streaming, Class<?> expectedClass) {
    KubernetesCachingAgentDispatcher dispatcher =
        new KubernetesCachingAgentDispatcher(
            new ObjectMapper(),
            new NoopRegistry(),
            new KubernetesConfigurationProperties(),
            new KubernetesSpinnakerKindMap(new ArrayList<>()),
            null,
            new NoOpStartupConcurrencyControl());
    KubernetesNamedAccountCredentials creds = mockCredentials(1, streaming);
    Collection<AbstractKubernetesCachingAgent> agents = dispatcher.buildAllCachingAgents(creds);

    assertNotNull(agents);
    assertEquals(1, agents.size());
    assertTrue(agents.stream().allMatch(agent -> expectedClass.isAssignableFrom(agent.getClass())));
  }

  @ParameterizedTest
  @MethodSource("agentTypes")
  public void buildAllCachingAgentsTwoThreads(boolean streaming, Class<?> expectedClass) {
    KubernetesCachingAgentDispatcher dispatcher =
        new KubernetesCachingAgentDispatcher(
            new ObjectMapper(),
            new NoopRegistry(),
            new KubernetesConfigurationProperties(),
            new KubernetesSpinnakerKindMap(new ArrayList<>()),
            null,
            new NoOpStartupConcurrencyControl());
    KubernetesNamedAccountCredentials creds = mockCredentials(2, streaming);
    Collection<AbstractKubernetesCachingAgent> agents = dispatcher.buildAllCachingAgents(creds);

    assertNotNull(agents);
    assertEquals(streaming ? 1 : 2, agents.size());
    assertTrue(agents.stream().allMatch(agent -> expectedClass.isAssignableFrom(agent.getClass())));
  }

  @ParameterizedTest
  @MethodSource("agentTypes")
  public void buildAllCachingAgentsCacheDisabled(boolean streaming, Class<?> expectedClass) {
    KubernetesConfigurationProperties configProperties = new KubernetesConfigurationProperties();
    configProperties.getCache().setEnabled(false);
    KubernetesCachingAgentDispatcher dispatcher =
        new KubernetesCachingAgentDispatcher(
            new ObjectMapper(),
            new NoopRegistry(),
            configProperties,
            new KubernetesSpinnakerKindMap(new ArrayList<>()),
            null,
            new NoOpStartupConcurrencyControl());
    KubernetesNamedAccountCredentials creds = mockCredentials(2, streaming);
    Collection<AbstractKubernetesCachingAgent> agents = dispatcher.buildAllCachingAgents(creds);

    assertNotNull(agents);
    assertEquals(0, agents.size());
  }

  static Stream<Arguments> agentTypes() {
    return Stream.of(
        Arguments.of(true, KubernetesStreamingCachingAgent.class),
        Arguments.of(false, KubernetesCoreCachingAgent.class));
  }

  @NotNull
  private KubernetesNamedAccountCredentials mockCredentials(int threads, boolean streaming) {
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
    KubernetesStreamingCachingProperties streamingCachingProperties =
        new KubernetesStreamingCachingProperties();
    streamingCachingProperties.setEnabled(streaming);
    when(namedCredentials.getStreamingCaching()).thenReturn(streamingCachingProperties);
    return namedCredentials;
  }
}
