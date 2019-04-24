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

package com.netflix.spinnaker.clouddriver.kubernetes.v2.caching.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spectator.api.Registry;
import com.netflix.spinnaker.cats.agent.AgentDataType;
import com.netflix.spinnaker.clouddriver.kubernetes.security.KubernetesNamedAccountCredentials;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.caching.Keys;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.description.KubernetesResourcePropertyRegistry;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.description.manifest.KubernetesKind;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.description.manifest.KubernetesManifest;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.security.KubernetesV2Credentials;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static com.netflix.spinnaker.cats.agent.AgentDataType.Authority.AUTHORITATIVE;
import static com.netflix.spinnaker.cats.agent.AgentDataType.Authority.INFORMATIVE;

@Slf4j
public class KubernetesNamespaceCachingAgent extends KubernetesV2CachingAgent {
  public KubernetesNamespaceCachingAgent(KubernetesNamedAccountCredentials<KubernetesV2Credentials> namedAccountCredentials,
      KubernetesResourcePropertyRegistry propertyRegistry,
      ObjectMapper objectMapper,
      Registry registry,
      int agentIndex,
      int agentCount,
      Long agentInterval) {
    super(namedAccountCredentials, propertyRegistry, objectMapper, registry, agentIndex, agentCount, agentInterval);
  }

  @Override
  protected Map<KubernetesKind, List<KubernetesManifest>> loadPrimaryResourceList() {
    reloadNamespaces();

    // TODO perf: Only load desired namespaces rather than filter all.
    Set<String> desired = new HashSet<>(this.namespaces);
    return Collections.singletonMap(KubernetesKind.NAMESPACE, credentials.list(KubernetesKind.NAMESPACE, "")
        .stream()
        .filter(ns -> desired.contains(ns.getName()))
        .collect(Collectors.toList()));
  }

  @Override
  protected List<KubernetesKind> primaryKinds() {
    return Collections.singletonList(KubernetesKind.NAMESPACE);
  }

  @Getter
  final private Collection<AgentDataType> providedDataTypes = Collections.unmodifiableSet(
      new HashSet<>(Arrays.asList(
          INFORMATIVE.forType(Keys.LogicalKind.APPLICATIONS.toString()),
          AUTHORITATIVE.forType(KubernetesKind.NAMESPACE.toString())
      ))
  );
}
