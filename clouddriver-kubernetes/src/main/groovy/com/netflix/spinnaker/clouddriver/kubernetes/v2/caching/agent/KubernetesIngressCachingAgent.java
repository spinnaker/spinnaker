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
import com.netflix.spinnaker.clouddriver.kubernetes.v2.description.manifest.KubernetesKind;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.description.manifest.KubernetesManifest;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.op.handler.KubernetesIngressHandler;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.security.KubernetesV2Credentials;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiFunction;
import java.util.stream.Collectors;

import static com.netflix.spinnaker.cats.agent.AgentDataType.Authority.AUTHORITATIVE;
import static com.netflix.spinnaker.cats.agent.AgentDataType.Authority.INFORMATIVE;

@Slf4j
public class KubernetesIngressCachingAgent extends KubernetesV2OnDemandCachingAgent {
  protected KubernetesIngressCachingAgent(KubernetesNamedAccountCredentials<KubernetesV2Credentials> namedAccountCredentials,
      ObjectMapper objectMapper,
      Registry registry,
      int agentIndex,
      int agentCount) {
    super(namedAccountCredentials, objectMapper, registry, agentIndex, agentCount);
  }

  @Getter
  final private Collection<AgentDataType> providedDataTypes = Collections.unmodifiableSet(
      new HashSet<>(Arrays.asList(
          INFORMATIVE.forType(Keys.LogicalKind.APPLICATIONS.toString()),
          INFORMATIVE.forType(KubernetesKind.SERVICE.toString()),
          AUTHORITATIVE.forType(KubernetesKind.INGRESS.toString())
      ))
  );

  @Override
  protected KubernetesKind primaryKind() {
    return KubernetesKind.INGRESS;
  }

  @Override
  protected Map<KubernetesManifest, List<KubernetesManifest>> loadSecondaryResourceRelationships(Map<KubernetesKind, List<KubernetesManifest>> ingresses) {
    Map<KubernetesManifest, List<KubernetesManifest>> result = new HashMap<>();

    BiFunction<String, String, String> manifestName = (namespace, name) -> namespace + ":" + name;

    Map<String, KubernetesManifest> services = namespaces.stream()
        .map(n -> credentials.list(KubernetesKind.SERVICE, n))
        .flatMap(Collection::stream)
        .collect(Collectors.toMap((m) -> manifestName.apply(m.getNamespace(), m.getName()), (m) -> m));

    for (KubernetesManifest ingress : ingresses.getOrDefault(primaryKind(), new ArrayList<>())) {
      List<KubernetesManifest> attachedServices = new ArrayList<>();
      try {
        attachedServices = KubernetesIngressHandler.attachedServices(ingress)
            .stream()
            .map(s -> services.get(manifestName.apply(ingress.getNamespace(), s)))
            .filter(Objects::nonNull)
            .collect(Collectors.toList());
      } catch (Exception e) {
        log.warn("Failure getting services attached to {}", ingress.getName(), e);
      }

      result.put(ingress, attachedServices);
    }

    return result;
  }
}
