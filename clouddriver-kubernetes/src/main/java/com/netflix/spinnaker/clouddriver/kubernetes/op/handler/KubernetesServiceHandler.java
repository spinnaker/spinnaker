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

package com.netflix.spinnaker.clouddriver.kubernetes.op.handler;

import static com.netflix.spinnaker.clouddriver.kubernetes.description.JsonPatch.Op.remove;
import static com.netflix.spinnaker.clouddriver.kubernetes.description.manifest.KubernetesApiVersion.V1;
import static com.netflix.spinnaker.clouddriver.kubernetes.description.manifest.KubernetesKind.REPLICA_SET;
import static com.netflix.spinnaker.clouddriver.kubernetes.description.manifest.KubernetesKind.SERVICE;
import static com.netflix.spinnaker.clouddriver.kubernetes.op.handler.KubernetesHandler.DeployPriority.NETWORK_RESOURCE_PRIORITY;

import com.google.common.collect.ImmutableMap;
import com.netflix.spinnaker.clouddriver.kubernetes.caching.Keys.InfrastructureCacheKey;
import com.netflix.spinnaker.clouddriver.kubernetes.caching.agent.KubernetesCacheDataConverter;
import com.netflix.spinnaker.clouddriver.kubernetes.caching.agent.KubernetesCachingAgentFactory;
import com.netflix.spinnaker.clouddriver.kubernetes.caching.agent.KubernetesCoreCachingAgent;
import com.netflix.spinnaker.clouddriver.kubernetes.description.JsonPatch;
import com.netflix.spinnaker.clouddriver.kubernetes.description.JsonPatch.Op;
import com.netflix.spinnaker.clouddriver.kubernetes.description.KubernetesCoordinates;
import com.netflix.spinnaker.clouddriver.kubernetes.description.SpinnakerKind;
import com.netflix.spinnaker.clouddriver.kubernetes.description.manifest.KubernetesKind;
import com.netflix.spinnaker.clouddriver.kubernetes.description.manifest.KubernetesManifest;
import com.netflix.spinnaker.clouddriver.kubernetes.model.Manifest.Status;
import io.kubernetes.client.openapi.models.V1Service;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import javax.annotation.Nonnull;
import javax.annotation.ParametersAreNonnullByDefault;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class KubernetesServiceHandler extends KubernetesHandler implements CanLoadBalance {
  @Override
  public int deployPriority() {
    return NETWORK_RESOURCE_PRIORITY.getValue();
  }

  @Nonnull
  @Override
  public KubernetesKind kind() {
    return KubernetesKind.SERVICE;
  }

  @Override
  public boolean versioned() {
    return false;
  }

  @Nonnull
  @Override
  public SpinnakerKind spinnakerKind() {
    return SpinnakerKind.LOAD_BALANCERS;
  }

  @Override
  public Status status(KubernetesManifest manifest) {
    return Status.defaultStatus();
  }

  @Override
  protected KubernetesCachingAgentFactory cachingAgentFactory() {
    return KubernetesCoreCachingAgent::new;
  }

  @Override
  public Map<String, Object> hydrateSearchResult(InfrastructureCacheKey key) {
    Map<String, Object> result = super.hydrateSearchResult(key);
    result.put("loadBalancer", result.get("name"));

    return result;
  }

  @Override
  public void addRelationships(
      Map<KubernetesKind, List<KubernetesManifest>> allResources,
      Map<KubernetesManifest, List<KubernetesManifest>> relationshipMap) {
    Map<String, Set<KubernetesManifest>> mapLabelToManifest = new HashMap<>();

    allResources
        .getOrDefault(REPLICA_SET, new ArrayList<>())
        .forEach(r -> addAllReplicaSetLabels(mapLabelToManifest, r));

    for (KubernetesManifest service : allResources.getOrDefault(SERVICE, new ArrayList<>())) {
      relationshipMap.put(service, getRelatedManifests(service, mapLabelToManifest));
    }
  }

  @Nonnull
  private ImmutableMap<String, String> getSelector(KubernetesManifest manifest) {
    if (manifest.getApiVersion().equals(V1)) {
      V1Service v1Service = KubernetesCacheDataConverter.getResource(manifest, V1Service.class);
      if (v1Service.getSpec() == null || v1Service.getSpec().getSelector() == null) {
        return ImmutableMap.of();
      }
      return ImmutableMap.copyOf(v1Service.getSpec().getSelector());
    } else {
      throw new IllegalArgumentException(
          "No services with version " + manifest.getApiVersion() + " supported");
    }
  }

  private List<KubernetesManifest> getRelatedManifests(
      KubernetesManifest service, Map<String, Set<KubernetesManifest>> mapLabelToManifest) {
    return new ArrayList<>(intersectLabels(service, mapLabelToManifest));
  }

  private Set<KubernetesManifest> intersectLabels(
      KubernetesManifest service, Map<String, Set<KubernetesManifest>> mapLabelToManifest) {
    ImmutableMap<String, String> selector = getSelector(service);
    if (selector.isEmpty()) {
      return new HashSet<>();
    }

    Set<KubernetesManifest> result = null;
    String namespace = service.getNamespace();
    for (Map.Entry<String, String> label : selector.entrySet()) {
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

  private void addAllReplicaSetLabels(
      Map<String, Set<KubernetesManifest>> entries, KubernetesManifest replicaSet) {
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

  private void enterManifest(
      Map<String, Set<KubernetesManifest>> entries, String label, KubernetesManifest manifest) {
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
  @ParametersAreNonnullByDefault
  public void attach(KubernetesManifest loadBalancer, KubernetesManifest target) {
    KubernetesCoordinates loadBalancerCoords = KubernetesCoordinates.fromManifest(loadBalancer);
    if (loadBalancerCoords.equals(KubernetesCoordinates.fromManifest(target))) {
      log.warn(
          "Adding traffic selection labels to service {}, which itself is the source load balancer. This may change in the future.",
          loadBalancerCoords);
    }
    Map<String, String> labels = target.getSpecTemplateLabels().orElse(target.getLabels());
    ImmutableMap<String, String> selector = getSelector(loadBalancer);
    if (selector.isEmpty()) {
      throw new IllegalArgumentException(
          "Service must have a non-empty selector in order to be attached to a workload");
    }
    if (!Collections.disjoint(labels.keySet(), selector.keySet())) {
      throw new IllegalArgumentException(
          "Service selector must have no label keys in common with target workload");
    }
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
  @ParametersAreNonnullByDefault
  public List<JsonPatch> attachPatch(KubernetesManifest loadBalancer, KubernetesManifest target) {
    String pathPrefix = pathPrefix(target);
    Map<String, String> labels = labels(target);

    return getSelector(loadBalancer).entrySet().stream()
        .map(
            kv ->
                JsonPatch.builder()
                    .op(labels.containsKey(kv.getKey()) ? Op.replace : Op.add)
                    .path(String.join("/", pathPrefix, JsonPatch.escapeNode(kv.getKey())))
                    .value(kv.getValue())
                    .build())
        .collect(Collectors.toList());
  }

  @Override
  @ParametersAreNonnullByDefault
  public List<JsonPatch> detachPatch(KubernetesManifest loadBalancer, KubernetesManifest target) {
    String pathPrefix = pathPrefix(target);
    Map<String, String> labels = labels(target);

    return getSelector(loadBalancer).keySet().stream()
        .filter(labels::containsKey)
        .map(
            k ->
                JsonPatch.builder()
                    .op(remove)
                    .path(String.join("/", pathPrefix, JsonPatch.escapeNode(k)))
                    .build())
        .collect(Collectors.toList());
  }
}
