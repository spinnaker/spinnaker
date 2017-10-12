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
import com.google.common.collect.ImmutableMap;
import com.netflix.spectator.api.Registry;
import com.netflix.spinnaker.cats.agent.AgentDataType;
import com.netflix.spinnaker.clouddriver.kubernetes.security.KubernetesNamedAccountCredentials;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.caching.Keys;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.description.manifest.KubernetesApiVersion;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.description.manifest.KubernetesKind;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.security.KubernetesV2Credentials;
import io.kubernetes.client.models.V1beta1ReplicaSet;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import static com.netflix.spinnaker.cats.agent.AgentDataType.Authority.AUTHORITATIVE;
import static com.netflix.spinnaker.cats.agent.AgentDataType.Authority.INFORMATIVE;

@Slf4j
public class KubernetesReplicaSetCachingAgent extends KubernetesV2OnDemandCachingAgent<V1beta1ReplicaSet> {
  KubernetesReplicaSetCachingAgent(KubernetesNamedAccountCredentials<KubernetesV2Credentials> namedAccountCredentials,
      ObjectMapper objectMapper,
      Registry registry,
      int agentIndex,
      int agentCount) {
    super(namedAccountCredentials, objectMapper, registry, agentIndex, agentCount);
  }

  @Getter
  final private Collection<AgentDataType> providedDataTypes = Collections.unmodifiableSet(
      new HashSet<>(Arrays.asList(
          INFORMATIVE.forType(Keys.LogicalKind.APPLICATION.toString()),
          INFORMATIVE.forType(Keys.LogicalKind.CLUSTER.toString()),
          INFORMATIVE.forType(KubernetesKind.DEPLOYMENT.toString()),
          AUTHORITATIVE.forType(KubernetesKind.REPLICA_SET.toString())
      ))
  );

  @Override
  protected List<V1beta1ReplicaSet> loadPrimaryResourceList() {
    return namespaces.stream()
        .map(credentials::listAllReplicaSets)
        .flatMap(Collection::stream)
        .collect(Collectors.toList());
  }

  @Override
  protected V1beta1ReplicaSet loadPrimaryResource(String namespace, String name) {
    return credentials.readReplicaSet(namespace, name);
  }

  @Override
  protected Class<V1beta1ReplicaSet> primaryResourceClass() {
    return V1beta1ReplicaSet.class;
  }

  @Override
  protected KubernetesKind primaryKind() {
    return KubernetesKind.REPLICA_SET;
  }

  @Override
  protected KubernetesApiVersion primaryApiVersion() {
    return KubernetesApiVersion.EXTENSIONS_V1BETA1;
  }
}
