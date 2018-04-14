/*
 * Copyright 2018 Google, Inc.
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
import com.netflix.spinnaker.clouddriver.kubernetes.v2.description.manifest.KubernetesApiVersion;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.description.manifest.KubernetesKind;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.description.manifest.KubernetesManifest;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.security.KubernetesV2Credentials;
import io.kubernetes.client.models.V1Event;
import io.kubernetes.client.models.V1ObjectReference;
import lombok.Getter;
import org.apache.commons.lang3.tuple.ImmutablePair;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static com.netflix.spinnaker.cats.agent.AgentDataType.Authority.AUTHORITATIVE;

public class KubernetesEventCachingAgent extends KubernetesV2CachingAgent {
  KubernetesEventCachingAgent(KubernetesNamedAccountCredentials<KubernetesV2Credentials> namedAccountCredentials,
      ObjectMapper objectMapper,
      Registry registry,
      int agentIndex,
      int agentCount) {
    super(namedAccountCredentials, objectMapper, registry, agentIndex, agentCount);
  }

  @Getter
  final private Collection<AgentDataType> providedDataTypes = Collections.unmodifiableSet(
      new HashSet<>(Arrays.asList(
          AUTHORITATIVE.forType(KubernetesKind.EVENT.toString())
      ))
  );

  @Override
  protected boolean hasClusterRelationship() {
    return false;
  }

  @Override
  protected KubernetesKind primaryKind() {
    return KubernetesKind.EVENT;
  }

  @Override
  protected Map<KubernetesManifest, List<KubernetesManifest>> loadSecondaryResourceRelationships(Map<KubernetesKind, List<KubernetesManifest>> primaryResourceList) {
    Map<KubernetesManifest, List<KubernetesManifest>> result = primaryResourceList.getOrDefault(primaryKind(), new ArrayList<>())
        .stream()
        .map(m -> ImmutablePair.of(m, KubernetesCacheDataConverter.getResource(m, V1Event.class)))
        .collect(Collectors.toMap(ImmutablePair::getLeft, p -> Collections.singletonList(involvedManifest(p.getRight()))));

    return result;
  }

  private KubernetesManifest involvedManifest(V1Event event) {
    V1ObjectReference ref = event.getInvolvedObject();
    KubernetesManifest result = new KubernetesManifest();
    result.put("metadata", new HashMap<String, Object>());

    result.setApiVersion(KubernetesApiVersion.fromString(ref.getApiVersion()));
    result.setKind(KubernetesKind.fromString(ref.getKind()));
    result.setNamespace(ref.getNamespace());
    result.setName(ref.getName());
    return result;
  }
}
