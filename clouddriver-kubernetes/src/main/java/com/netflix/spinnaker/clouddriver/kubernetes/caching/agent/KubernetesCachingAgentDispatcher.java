/*
 * Copyright 2017 Google, Inc.
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

package com.netflix.spinnaker.clouddriver.kubernetes.caching.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spectator.api.Registry;
import com.netflix.spinnaker.clouddriver.kubernetes.description.KubernetesResourceProperties;
import com.netflix.spinnaker.clouddriver.kubernetes.description.ResourcePropertyRegistry;
import com.netflix.spinnaker.clouddriver.kubernetes.security.KubernetesCredentials;
import com.netflix.spinnaker.clouddriver.kubernetes.security.KubernetesNamedAccountCredentials;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class KubernetesCachingAgentDispatcher {
  private final ObjectMapper objectMapper;
  private final Registry registry;

  @Autowired
  public KubernetesCachingAgentDispatcher(ObjectMapper objectMapper, Registry registry) {
    this.objectMapper = objectMapper;
    this.registry = registry;
  }

  public Collection<KubernetesCachingAgent> buildAllCachingAgents(
      KubernetesNamedAccountCredentials credentials) {
    KubernetesCredentials kubernetesCredentials = credentials.getCredentials();
    List<KubernetesCachingAgent> result = new ArrayList<>();
    Long agentInterval =
        Optional.ofNullable(credentials.getCacheIntervalSeconds())
            .map(TimeUnit.SECONDS::toMillis)
            .orElse(null);

    ResourcePropertyRegistry propertyRegistry = kubernetesCredentials.getResourcePropertyRegistry();

    IntStream.range(0, credentials.getCacheThreads())
        .forEach(
            i ->
                propertyRegistry.values().stream()
                    .map(KubernetesResourceProperties::getHandler)
                    .map(
                        h ->
                            h.buildCachingAgent(
                                credentials,
                                objectMapper,
                                registry,
                                i,
                                credentials.getCacheThreads(),
                                agentInterval))
                    .filter(Objects::nonNull)
                    .forEach(result::add));

    return result.stream()
        .collect(Collectors.toMap(KubernetesCachingAgent::getAgentType, c -> c, (a, b) -> b))
        .values();
  }
}
