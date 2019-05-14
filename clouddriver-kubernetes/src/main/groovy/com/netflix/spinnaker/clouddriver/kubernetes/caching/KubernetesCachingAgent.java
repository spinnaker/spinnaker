/*
 * Copyright 2017 Google, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package com.netflix.spinnaker.clouddriver.kubernetes.caching;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spectator.api.Registry;
import com.netflix.spinnaker.cats.agent.AccountAware;
import com.netflix.spinnaker.cats.agent.CachingAgent;
import com.netflix.spinnaker.clouddriver.kubernetes.security.KubernetesCredentials;
import com.netflix.spinnaker.clouddriver.kubernetes.security.KubernetesNamedAccountCredentials;
import java.util.List;
import java.util.stream.Collectors;
import lombok.Getter;

public abstract class KubernetesCachingAgent<C extends KubernetesCredentials>
    implements CachingAgent, AccountAware {
  @Getter protected final String accountName;
  protected final Registry registry;
  protected final C credentials;
  protected final ObjectMapper objectMapper;

  protected final int agentIndex;
  protected final int agentCount;

  protected List<String> namespaces;

  public List<String> getNamespaces() {
    return namespaces;
  }

  protected KubernetesCachingAgent(
      KubernetesNamedAccountCredentials<C> namedAccountCredentials,
      ObjectMapper objectMapper,
      Registry registry,
      int agentIndex,
      int agentCount) {
    this.accountName = namedAccountCredentials.getName();
    this.credentials = namedAccountCredentials.getCredentials();
    this.objectMapper = objectMapper;
    this.registry = registry;

    this.agentIndex = agentIndex;
    this.agentCount = agentCount;

    reloadNamespaces();
  }

  @Override
  public String getAgentType() {
    return String.format(
        "%s/%s[%d/%d]", accountName, this.getClass().getSimpleName(), agentIndex + 1, agentCount);
  }

  protected void reloadNamespaces() {
    namespaces =
        credentials.getDeclaredNamespaces().stream()
            .filter(n -> agentCount == 1 || Math.abs(n.hashCode() % agentCount) == agentIndex)
            .collect(Collectors.toList());
  }
}
