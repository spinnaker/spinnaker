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

import com.netflix.spinnaker.clouddriver.kubernetes.caching.KubernetesProvider;
import com.netflix.spinnaker.clouddriver.kubernetes.caching.agent.KubernetesCachingAgent;
import com.netflix.spinnaker.clouddriver.kubernetes.caching.agent.KubernetesCachingAgentDispatcher;
import com.netflix.spinnaker.clouddriver.kubernetes.config.KubernetesConfigurationProperties;
import com.netflix.spinnaker.credentials.CredentialsLifecycleHandler;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class KubernetesCredentialsLifecycleHandler
    implements CredentialsLifecycleHandler<KubernetesNamedAccountCredentials> {
  private static final Logger log =
      LoggerFactory.getLogger(KubernetesCredentialsLifecycleHandler.class);
  private final KubernetesProvider provider;
  private final KubernetesCachingAgentDispatcher cachingAgentDispatcher;
  private final KubernetesConfigurationProperties kubernetesConfigurationProperties;

  @Override
  public void credentialsAdded(KubernetesNamedAccountCredentials credentials) {
    if (kubernetesConfigurationProperties.isLoadNamespacesInAccount()) {
      // Attempt to get namespaces to resolve any connectivity error without blocking /credentials
      log.info(
          "kubernetes.loadNamespacesInAccount flag is set to true - loading all namespaces for new account: {}",
          credentials.getName());
      List<String> namespaces = credentials.getCredentials().getDeclaredNamespaces();
      if (namespaces.isEmpty()) {
        log.warn(
            "New account {} did not return any namespace and could be unreachable or misconfigured",
            credentials.getName());
      }
    } else {
      log.info(
          "kubernetes.loadNamespacesInAccount flag is disabled - new account: {} is unverified",
          credentials.getName());
    }

    Collection<KubernetesCachingAgent> newlyAddedAgents =
        cachingAgentDispatcher.buildAllCachingAgents(credentials);

    log.info("Adding {} agents for new account {}", newlyAddedAgents.size(), credentials.getName());
    provider.addAgents(newlyAddedAgents);
  }

  @Override
  public void credentialsUpdated(KubernetesNamedAccountCredentials credentials) {
    // Attempt to get namespaces to resolve any connectivity error without blocking /credentials
    List<String> namespaces = credentials.getCredentials().getDeclaredNamespaces();
    if (namespaces.isEmpty()) {
      log.warn(
          "Modified account {} did not return any namespace and could be unreachable or misconfigured",
          credentials.getName());
    }

    Collection<KubernetesCachingAgent> updatedAgents =
        cachingAgentDispatcher.buildAllCachingAgents(credentials);

    log.info(
        "Scheduling {} agents for updated account {}", updatedAgents.size(), credentials.getName());
    // Remove existing agents belonging to changed accounts
    provider.removeAgentsForAccounts(Collections.singleton(credentials.getName()));
    provider.addAgents(updatedAgents);
  }

  @Override
  public void credentialsDeleted(KubernetesNamedAccountCredentials credentials) {
    provider.removeAgentsForAccounts(Collections.singleton(credentials.getName()));
  }
}
