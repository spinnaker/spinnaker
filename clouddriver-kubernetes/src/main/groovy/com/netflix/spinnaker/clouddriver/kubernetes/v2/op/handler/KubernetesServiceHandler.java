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

package com.netflix.spinnaker.clouddriver.kubernetes.v2.op.handler;

import com.netflix.spinnaker.clouddriver.kubernetes.v2.caching.Keys;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.caching.agent.KubernetesCacheDataConverter;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.caching.agent.KubernetesCoreCachingAgent;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.caching.agent.KubernetesV2CachingAgentFactory;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.caching.view.provider.KubernetesCacheUtils;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.description.JsonPatch;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.description.JsonPatch.Op;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.description.KubernetesSpinnakerKindMap.SpinnakerKind;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.description.manifest.KubernetesKind;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.description.manifest.KubernetesManifest;
import com.netflix.spinnaker.clouddriver.model.Manifest.Status;
import io.kubernetes.client.models.V1Service;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static com.netflix.spinnaker.clouddriver.kubernetes.v2.description.JsonPatch.Op.remove;
import static com.netflix.spinnaker.clouddriver.kubernetes.v2.description.manifest.KubernetesApiVersion.V1;
import static com.netflix.spinnaker.clouddriver.kubernetes.v2.description.manifest.KubernetesKind.REPLICA_SET;
import static com.netflix.spinnaker.clouddriver.kubernetes.v2.description.manifest.KubernetesKind.SERVICE;
import static com.netflix.spinnaker.clouddriver.kubernetes.v2.op.handler.KubernetesHandler.DeployPriority.NETWORK_RESOURCE_PRIORITY;

@Component
public class KubernetesServiceHandler extends KubernetesHandler implements CanLoadBalance {
  @Override
  public int deployPriority() {
    return NETWORK_RESOURCE_PRIORITY.getValue();
  }

  @Override
  public KubernetesKind kind() {
    return KubernetesKind.SERVICE;
  }

  @Override
  public boolean versioned() {
    return false;
  }

  @Override
  public SpinnakerKind spinnakerKind() {
    return SpinnakerKind.LOAD_BALANCERS;
  }

  @Override
  public Status status(KubernetesManifest manifest) {
    return new Status();
  }

  @Override
  protected KubernetesV2CachingAgentFactory cachingAgentFactory() {
    return KubernetesCoreCachingAgent::new;
  }

  @Override
  public Map<String, Object> hydrateSearchResult(Keys.InfrastructureCacheKey key, KubernetesCacheUtils cacheUtils) {
    Map<String, Object> result = super.hydrateSearchResult(key, cacheUtils);
    result.put("loadBalancer", result.get("name"));

    return result;
  }

  @Override
  public void addRelationships(Map<KubernetesKind, List<KubernetesManifest>> allResources, Map<KubernetesManifest, List<KubernetesManifest>> relationshipMap) {
    Map<String, Set<KubernetesManifest>> mapLabelToManifest = new HashMap<>();

    allResources.getOrDefault(REPLICA_SET, new ArrayList<>())
        .forEach(r -> addAllReplicaSetLabels(mapLabelToManifest, r));

    for (KubernetesManifest service : allResources.getOrDefault(SERVICE, new ArrayList<>())) {
      relationshipMap.put(service, getRelatedManifests(service, mapLabelToManifest));
    }
  }

  private Map<String, String> getSelector(KubernetesManifest manifest) {
    if (manifest.getApiVersion().equals(V1)) {
      V1Service v1Service = KubernetesCacheDataConverter.getResource(manifest, V1Service.class);
      return v1Service.getSpec().getSelector();
    } else {
      throw new IllegalArgumentException("No services with version " + manifest.getApiVersion() + " supported");
    }
  }

  private List<KubernetesManifest> getRelatedManifests(KubernetesManifest service, Map<String, Set<KubernetesManifest>> mapLabelToManifest) {
    return new ArrayList<>(intersectLabels(service, mapLabelToManifest));
  }

  private Set<KubernetesManifest> intersectLabels(KubernetesManifest service, Map<String, Set<KubernetesManifest>> mapLabelToManifest) {
    Map<String, String> selector = getSelector(service);
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

  private void addAllReplicaSetLabels(Map<String, Set<KubernetesManifest>> entries, KubernetesManifest replicaSet) {
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

  private void enterManifest(Map<String, Set<KubernetesManifest>> entries, String label, KubernetesManifest manifest) {
    Set<KubernetesManifest> pods = entries.get(label);
    if (pods == null) {
      pods = new HashSet<>();
    }

    pods.add(manifest);

    entries.put(label, pods);
  }

  private String podLabelKey(String namespace, Map.Entry<String, String> label) {
    // Space can't be used in any of the values, so it's a safe separator.
    return namespace + " " + label.getKey() + " " + label.getValue();
  }

  @Override
  public void attach(KubernetesManifest loadBalancer, KubernetesManifest target) {
    Map<String, String> labels = target.getSpecTemplateLabels().orElse(target.getLabels());
    Map<String, String> selector = getSelector(loadBalancer);
    labels.putAll(selector);
  }

  private String pathPrefix(KubernetesManifest target) {
    if (target.getSpecTemplateLabels().isPresent()) {
      return "/spec/template/metadata/labels";
    } else {
      return "/metadata/labels";
    }
  }

  private Map<String, String> labels(KubernetesManifest target) {
    if (target.getSpecTemplateLabels().isPresent()) {
      return target.getSpecTemplateLabels().get();
    } else {
      return target.getLabels();
    }
  }

  @Override
  public List<JsonPatch> attachPatch(KubernetesManifest loadBalancer, KubernetesManifest target) {
    String pathPrefix = pathPrefix(target);
    Map<String, String> labels = labels(target);

    return getSelector(loadBalancer).entrySet().stream()
        .map(kv -> JsonPatch.builder()
            .op(labels.containsKey(kv.getKey()) ? Op.replace : Op.add)
            .path(String.join("/", pathPrefix, kv.getKey()))
            .value(kv.getValue())
            .build())
        .collect(Collectors.toList());
  }

  @Override
  public List<JsonPatch> detachPatch(KubernetesManifest loadBalancer, KubernetesManifest target) {
    String pathPrefix = pathPrefix(target);
    Map<String, String> labels = labels(target);

    return getSelector(loadBalancer).keySet().stream()
        .filter(labels::containsKey)
        .map(k -> JsonPatch.builder().op(remove).path(String.join("/", pathPrefix, k)).build())
        .collect(Collectors.toList());
  }
}
