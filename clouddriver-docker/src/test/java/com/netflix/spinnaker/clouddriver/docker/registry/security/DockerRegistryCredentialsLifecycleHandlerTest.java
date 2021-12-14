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

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.netflix.spinnaker.clouddriver.docker.registry.DockerRegistryCloudProvider;
import com.netflix.spinnaker.clouddriver.docker.registry.provider.DockerRegistryProvider;
import com.netflix.spinnaker.clouddriver.docker.registry.provider.agent.DockerRegistryImageCachingAgent;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.platform.runner.JUnitPlatform;
import org.junit.runner.RunWith;

@RunWith(JUnitPlatform.class)
class DockerRegistryCredentialsLifecycleHandlerTest {

  @Test
  public void testAddCredentials() {
    DockerRegistryCloudProvider dockerRegistryCloudProvider = new DockerRegistryCloudProvider();
    DockerRegistryProvider provider = new DockerRegistryProvider(dockerRegistryCloudProvider);
    DockerRegistryCredentialsLifecycleHandler handler =
        new DockerRegistryCredentialsLifecycleHandler(provider, dockerRegistryCloudProvider);

    // Check we start with no agents
    assertThat(provider.getAgents()).isEmpty();

    DockerRegistryNamedAccountCredentials dockerRegistryNamedAccountCredentials =
        mock(DockerRegistryNamedAccountCredentials.class);
    DockerRegistryCredentials dockerRegistryCredentials = mock(DockerRegistryCredentials.class);

    when(dockerRegistryNamedAccountCredentials.getName()).thenReturn("docker");
    when(dockerRegistryNamedAccountCredentials.getCredentials())
        .thenReturn(dockerRegistryCredentials);
    when(dockerRegistryNamedAccountCredentials.getCacheThreads()).thenReturn(1);
    when(dockerRegistryNamedAccountCredentials.getCacheIntervalSeconds()).thenReturn(10L);
    when(dockerRegistryNamedAccountCredentials.getRegistry()).thenReturn("registry");

    handler.credentialsAdded(dockerRegistryNamedAccountCredentials);
    // We should have added an agent
    assertThat(provider.getAgents()).hasSize(1);

    handler.credentialsAdded(dockerRegistryNamedAccountCredentials);
    // We should have yet another one
    assertThat(provider.getAgents()).hasSize(2);
  }

  @Test
  public void testMultipleCacheThreads() {
    DockerRegistryCloudProvider dockerRegistryCloudProvider = new DockerRegistryCloudProvider();
    DockerRegistryProvider provider = new DockerRegistryProvider(dockerRegistryCloudProvider);
    DockerRegistryCredentialsLifecycleHandler handler =
        new DockerRegistryCredentialsLifecycleHandler(provider, dockerRegistryCloudProvider);

    // Check we start with no agents
    assertThat(provider.getAgents()).isEmpty();

    DockerRegistryNamedAccountCredentials dockerRegistryNamedAccountCredentials =
        mock(DockerRegistryNamedAccountCredentials.class);
    DockerRegistryCredentials dockerRegistryCredentials = mock(DockerRegistryCredentials.class);

    when(dockerRegistryNamedAccountCredentials.getName()).thenReturn("docker");
    when(dockerRegistryNamedAccountCredentials.getCredentials())
        .thenReturn(dockerRegistryCredentials);

    final int cacheThreads = 3;
    when(dockerRegistryNamedAccountCredentials.getCacheThreads()).thenReturn(cacheThreads);
    when(dockerRegistryNamedAccountCredentials.getCacheIntervalSeconds()).thenReturn(10L);
    when(dockerRegistryNamedAccountCredentials.getRegistry()).thenReturn("registry");

    handler.credentialsAdded(dockerRegistryNamedAccountCredentials);
    // We should have added an agent per cache thread
    assertThat(provider.getAgents()).hasSize(cacheThreads);
  }

  @Test
  public void testUpdateCredentials() {
    DockerRegistryCloudProvider dockerRegistryCloudProvider = new DockerRegistryCloudProvider();
    DockerRegistryProvider provider = new DockerRegistryProvider(dockerRegistryCloudProvider);
    DockerRegistryCredentialsLifecycleHandler handler =
        new DockerRegistryCredentialsLifecycleHandler(provider, dockerRegistryCloudProvider);

    // Check we start with no agents
    assertThat(provider.getAgents()).isEmpty();

    DockerRegistryNamedAccountCredentials dockerRegistryNamedAccountCredentials =
        mock(DockerRegistryNamedAccountCredentials.class);
    DockerRegistryCredentials dockerRegistryCredentials = mock(DockerRegistryCredentials.class);

    when(dockerRegistryNamedAccountCredentials.getName()).thenReturn("docker");
    when(dockerRegistryNamedAccountCredentials.getCredentials())
        .thenReturn(dockerRegistryCredentials);
    when(dockerRegistryNamedAccountCredentials.getCacheThreads()).thenReturn(1);
    when(dockerRegistryNamedAccountCredentials.getCacheIntervalSeconds()).thenReturn(10L);
    when(dockerRegistryNamedAccountCredentials.getRegistry()).thenReturn("registry");

    handler.credentialsAdded(dockerRegistryNamedAccountCredentials);
    // We should have added an agent
    assertThat(provider.getAgents()).hasSize(1);
    DockerRegistryImageCachingAgent agent =
        ((DockerRegistryImageCachingAgent) provider.getAgents().stream().findFirst().get());
    assertEquals(10000L, agent.getAgentInterval());

    // updating a field
    when(dockerRegistryNamedAccountCredentials.getCacheIntervalSeconds()).thenReturn(20L);
    handler.credentialsUpdated(dockerRegistryNamedAccountCredentials);
    // We should have only one
    assertThat(provider.getAgents()).hasSize(1);

    agent = ((DockerRegistryImageCachingAgent) provider.getAgents().stream().findFirst().get());
    assertEquals(20000L, agent.getAgentInterval());
  }

  @Test
  public void testRemoveCredentials() {
    String ACCOUNT1 = "account1";
    String ACCOUNT2 = "account2";

    DockerRegistryCloudProvider dockerRegistryCloudProvider = new DockerRegistryCloudProvider();
    DockerRegistryProvider provider = new DockerRegistryProvider(dockerRegistryCloudProvider);
    DockerRegistryCredentialsLifecycleHandler handler =
        new DockerRegistryCredentialsLifecycleHandler(provider, dockerRegistryCloudProvider);

    // Check we start with no agents
    assertThat(provider.getAgents()).isEmpty();

    DockerRegistryImageCachingAgent agent1 = mock(DockerRegistryImageCachingAgent.class);
    when(agent1.handlesAccount(ACCOUNT1)).thenReturn(true);

    DockerRegistryImageCachingAgent agent2 = mock(DockerRegistryImageCachingAgent.class);
    when(agent1.handlesAccount(ACCOUNT2)).thenReturn(true);

    provider.addAgents(List.of(agent1, agent2));
    assertThat(provider.getAgents()).hasSize(2);

    DockerRegistryNamedAccountCredentials cred1 = mock(DockerRegistryNamedAccountCredentials.class);
    when(cred1.getName()).thenReturn(ACCOUNT1);
    handler.credentialsDeleted(cred1);

    // We removed account1 so only agent2 should remain
    assertThat(provider.getAgents()).hasSize(1);
    assertThat(provider.getAgents()).contains(agent2);
  }
}
