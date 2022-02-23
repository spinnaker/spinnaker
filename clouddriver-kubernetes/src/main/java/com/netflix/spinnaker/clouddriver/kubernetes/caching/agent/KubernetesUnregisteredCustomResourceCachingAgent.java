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
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.netflix.spectator.api.Registry;
import com.netflix.spinnaker.cats.agent.AgentDataType;
import com.netflix.spinnaker.clouddriver.kubernetes.config.KubernetesConfigurationProperties;
import com.netflix.spinnaker.clouddriver.kubernetes.description.KubernetesSpinnakerKindMap;
import com.netflix.spinnaker.clouddriver.kubernetes.description.manifest.KubernetesKind;
import com.netflix.spinnaker.clouddriver.kubernetes.security.KubernetesCredentials;
import com.netflix.spinnaker.clouddriver.kubernetes.security.KubernetesNamedAccountCredentials;

/**
 * Instances of this class cache CRDs for one particular account at regular intervals.
 *
 * <p>The list of CRDs to cache are the ones dynamically returned from "kubectl get crd" calls in
 * {@link KubernetesCredentials#getCrds()}, so the kinds cached in this class change dynamically if
 * CRDs are added or deleted from the cluster of a particular account. From this list, only the
 * kinds to which clouddriver has access (kubectl get {kind}) and are allowed by configuration are
 * cached.
 */
public class KubernetesUnregisteredCustomResourceCachingAgent extends KubernetesCachingAgent {
  public KubernetesUnregisteredCustomResourceCachingAgent(
      KubernetesNamedAccountCredentials namedAccountCredentials,
      ObjectMapper objectMapper,
      Registry registry,
      int agentIndex,
      int agentCount,
      Long agentInterval,
      KubernetesConfigurationProperties configurationProperties,
      KubernetesSpinnakerKindMap kubernetesSpinnakerKindMap) {
    super(
        namedAccountCredentials,
        objectMapper,
        registry,
        agentIndex,
        agentCount,
        agentInterval,
        configurationProperties,
        kubernetesSpinnakerKindMap);
  }

  @Override
  public ImmutableSet<AgentDataType> getProvidedDataTypes() {
    return filteredPrimaryKinds().stream()
        .map(k -> AUTHORITATIVE.forType(k.toString()))
        .collect(toImmutableSet());
  }

  @Override
  protected ImmutableList<KubernetesKind> primaryKinds() {
    return credentials.getCrds();
  }
}
