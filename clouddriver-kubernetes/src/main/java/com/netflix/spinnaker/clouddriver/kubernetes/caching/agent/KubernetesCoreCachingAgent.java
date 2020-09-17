/*
 * Copyright 2018 Google, Inc.
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

import static com.google.common.collect.ImmutableSet.toImmutableSet;
import static com.netflix.spinnaker.cats.agent.AgentDataType.Authority.AUTHORITATIVE;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spectator.api.Registry;
import com.netflix.spinnaker.cats.agent.AgentDataType;
import com.netflix.spinnaker.clouddriver.kubernetes.caching.Keys;
import com.netflix.spinnaker.clouddriver.kubernetes.description.manifest.KubernetesKind;
import com.netflix.spinnaker.clouddriver.kubernetes.security.KubernetesNamedAccountCredentials;
import java.util.Collection;
import java.util.List;
import java.util.stream.Stream;

public class KubernetesCoreCachingAgent extends KubernetesCachingAgent {
  public KubernetesCoreCachingAgent(
      KubernetesNamedAccountCredentials namedAccountCredentials,
      ObjectMapper objectMapper,
      Registry registry,
      int agentIndex,
      int agentCount,
      Long agentInterval) {
    super(namedAccountCredentials, objectMapper, registry, agentIndex, agentCount, agentInterval);
  }

  @Override
  public Collection<AgentDataType> getProvidedDataTypes() {
    // The ARTIFACT kind is deprecated; no new entries of this type will be created. We are leaving
    // it in the authoritative types for now so that existing entries get evicted.
    @SuppressWarnings("deprecation")
    Stream<String> logicalTypes =
        Stream.of(Keys.LogicalKind.APPLICATIONS, Keys.LogicalKind.CLUSTERS, Keys.Kind.ARTIFACT)
            .map(Enum::toString);
    Stream<String> kubernetesTypes = primaryKinds().stream().map(KubernetesKind::toString);

    return Stream.concat(logicalTypes, kubernetesTypes)
        .map(AUTHORITATIVE::forType)
        .collect(toImmutableSet());
  }

  @Override
  protected List<KubernetesKind> primaryKinds() {
    return credentials.getGlobalKinds();
  }
}
