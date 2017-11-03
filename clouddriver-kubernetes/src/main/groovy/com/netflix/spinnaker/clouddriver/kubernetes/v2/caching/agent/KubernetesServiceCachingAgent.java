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
import com.netflix.spinnaker.clouddriver.kubernetes.v2.op.deployer.KubernetesReplicaSetHandler;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.op.deployer.KubernetesServiceHandler;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.op.job.KubectlJobExecutor;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.security.KubernetesV2Credentials;
import lombok.Getter;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static com.netflix.spinnaker.cats.agent.AgentDataType.Authority.AUTHORITATIVE;
import static com.netflix.spinnaker.cats.agent.AgentDataType.Authority.INFORMATIVE;

public class KubernetesServiceCachingAgent extends KubernetesV2OnDemandCachingAgent {
  protected KubernetesServiceCachingAgent(KubernetesNamedAccountCredentials<KubernetesV2Credentials> namedAccountCredentials,
      KubectlJobExecutor jobExecutor,
      ObjectMapper objectMapper,
      Registry registry,
      int agentIndex,
      int agentCount) {
    super(namedAccountCredentials, jobExecutor, objectMapper, registry, agentIndex, agentCount);
  }

  @Getter
  final private Collection<AgentDataType> providedDataTypes = Collections.unmodifiableSet(
      new HashSet<>(Arrays.asList(
          INFORMATIVE.forType(Keys.LogicalKind.APPLICATION.toString()),
          INFORMATIVE.forType(KubernetesKind.POD.toString()),
          INFORMATIVE.forType(KubernetesKind.REPLICA_SET.toString()),
          AUTHORITATIVE.forType(KubernetesKind.SERVICE.toString())
      ))
  );

  @Override
  protected KubernetesKind primaryKind() {
    return KubernetesKind.SERVICE;
  }

  @Override
  protected Map<KubernetesManifest, List<KubernetesManifest>> loadSecondaryResourceRelationships(List<KubernetesManifest> services) {
    Map<String, Set<KubernetesManifest>> mapLabelToManifest = new HashMap<>();

    // TODO perf - this might be excessive when only a small number of services are specified. We could consider
    // reading from the cache here, or deciding how many pods to load ahead of time, or construct a fancy label
    // selector that merges all label selectors here.
    namespaces.stream()
        .map(n -> jobExecutor.getAll(credentials, KubernetesKind.REPLICA_SET, n))
        .flatMap(Collection::stream)
        .collect(Collectors.toList())
        .forEach(r -> addAllReplicaSetLabels(mapLabelToManifest, r));

    Map<KubernetesManifest, List<KubernetesManifest>> result = new HashMap<>();

    for (KubernetesManifest service : services) {
      result.put(service, getRelatedManifests(service, mapLabelToManifest));
    }

    return result;
  }

  private static List<KubernetesManifest> getRelatedManifests(KubernetesManifest service, Map<String, Set<KubernetesManifest>> mapLabelToManifest) {
    return new ArrayList<>(intersectLabels(service, mapLabelToManifest));
  }

  private static Set<KubernetesManifest> intersectLabels(KubernetesManifest service, Map<String, Set<KubernetesManifest>> mapLabelToManifest) {
    Map<String, String> selector = KubernetesServiceHandler.getSelector(service);
    if (selector == null || selector.isEmpty()) {
      return new HashSet<>();
    }

    Set<KubernetesManifest> result = null;
    String namespace = service.getNamespace();
    for (Map.Entry<String, String> label : selector.entrySet())  {
      String labelKey = podLabelKey(namespace, label);
      Set<KubernetesManifest> manifests = mapLabelToManifest.get(labelKey);
      manifests = manifests == null ? new HashSet<>() : manifests;

      if (result == null) {
        result = manifests;
      } else {
        result.retainAll(manifests);
      }
    }

    return result;
  }

  private static void addAllReplicaSetLabels(Map<String, Set<KubernetesManifest>> entries, KubernetesManifest replicaSet) {
    String namespace = replicaSet.getNamespace();
    Map<String, String> podLabels = KubernetesReplicaSetHandler.getPodTemplateLabels(replicaSet);
    if (podLabels == null) {
      return;
    }

    for (Map.Entry<String, String> label : podLabels.entrySet()) {
      String labelKey = podLabelKey(namespace, label);
      enterManifest(entries, labelKey, KubernetesCacheDataConverter.convertToManifest(replicaSet));
    }
  }

  private static void enterManifest(Map<String, Set<KubernetesManifest>> entries, String label, KubernetesManifest manifest) {
    Set<KubernetesManifest> pods = entries.get(label);
    if (pods == null) {
      pods = new HashSet<>();
    }

    pods.add(manifest);

    entries.put(label, pods);
  }

  private static String podLabelKey(String namespace, Map.Entry<String, String> label) {
    // Space can't be used in any of the values, so it's a safe separator.
    return namespace + " " + label.getKey() + " " + label.getValue();
  }
}
