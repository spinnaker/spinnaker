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
import com.netflix.spinnaker.clouddriver.kubernetes.v2.description.manifest.KubernetesApiVersion;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.description.manifest.KubernetesKind;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.description.manifest.KubernetesManifest;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.security.KubernetesV2Credentials;
import io.kubernetes.client.models.V1Pod;
import io.kubernetes.client.models.V1Service;
import lombok.Getter;

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

public class KubernetesServiceCachingAgent extends KubernetesV2OnDemandCachingAgent<V1Service> {
  protected KubernetesServiceCachingAgent(KubernetesNamedAccountCredentials<KubernetesV2Credentials> namedAccountCredentials,
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
          AUTHORITATIVE.forType(KubernetesKind.SERVICE.toString())
      ))
  );

  @Override
  protected List<V1Service> loadPrimaryResourceList() {
    return namespaces.stream()
        .map(credentials::listAllServices)
        .flatMap(Collection::stream)
        .collect(Collectors.toList());
  }

  @Override
  protected V1Service loadPrimaryResource(String namespace, String name) {
    return credentials.readService(namespace, name);
  }

  @Override
  protected Class<V1Service> primaryResourceClass() {
    return V1Service.class;
  }

  @Override
  protected KubernetesKind primaryKind() {
    return KubernetesKind.SERVICE;
  }

  @Override
  protected KubernetesApiVersion primaryApiVersion() {
    return KubernetesApiVersion.V1;
  }

  @Override
  protected Map<V1Service, List<KubernetesManifest>> loadSecondaryResourceRelationships(List<V1Service> services) {
    // TODO perf - this might be excessive when only a small number of services are specified. We could consider
    // reading from the cache here, or deciding how many pods to load ahead of time, or construct a fancy label
    // selector that merges all label selectors here.
    List<V1Pod> pods = namespaces.stream()
        .map(credentials::listAllPods)
        .flatMap(Collection::stream)
        .collect(Collectors.toList());

    Map<String, Set<V1Pod>> mapLabelToPod = new HashMap<>();
    Map<V1Service, List<KubernetesManifest>> result = new HashMap<>();

    for (V1Pod pod : pods) {
      addAllPodLabels(mapLabelToPod, pod);
    }

    for (V1Service service : services) {
      result.put(service, getRelatedManifests(service, mapLabelToPod));
    }

    return result;
  }

  private static List<KubernetesManifest> getRelatedManifests(V1Service service, Map<String, Set<V1Pod>> mapLabelToPod) {
    return intersectLabels(service, mapLabelToPod).stream()
        .map(KubernetesCacheDataConverter::convertToManifest)
        .collect(Collectors.toList());
  }

  private static Set<V1Pod> intersectLabels(V1Service service, Map<String, Set<V1Pod>> mapLabelToPod) {
    Map<String, String> selector = service.getSpec().getSelector();
    if (selector == null || selector.isEmpty()) {
      return new HashSet<>();
    }

    Set<V1Pod> result = null;
    String namespace = service.getMetadata().getNamespace();
    for (Map.Entry<String, String> label : service.getSpec().getSelector().entrySet())  {
      String labelKey = podLabelKey(namespace, label);
      Set<V1Pod> pods = mapLabelToPod.get(labelKey);
      pods = pods == null ? new HashSet<>() : pods;

      if (result == null) {
        result = pods;
      } else {
        result.retainAll(pods);
      }
    }

    return result;
  }

  private static void addAllPodLabels(Map<String, Set<V1Pod>> entries, V1Pod pod) {
    String namespace = pod.getMetadata().getNamespace();
    for (Map.Entry<String, String> label : pod.getMetadata().getLabels().entrySet()) {
      String labelKey = podLabelKey(namespace, label);
      enterPod(entries, labelKey, pod);
    }
  }

  private static void enterPod(Map<String, Set<V1Pod>> entries, String label, V1Pod pod) {
    Set<V1Pod> pods = entries.get(label);
    if (pods == null) {
      pods = new HashSet<>();
    }

    pods.add(pod);

    entries.put(label, pods);
  }

  private static String podLabelKey(String namespace, Map.Entry<String, String> label) {
    // Space can't be used in any of the values, so it's a safe separator.
    return namespace + " " + label.getKey() + " " + label.getValue();
  }
}
