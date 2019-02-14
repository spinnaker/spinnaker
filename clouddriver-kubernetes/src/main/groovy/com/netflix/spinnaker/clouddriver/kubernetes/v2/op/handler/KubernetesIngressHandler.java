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
import com.netflix.spinnaker.clouddriver.kubernetes.v2.caching.agent.KubernetesV2CachingAgent;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.caching.view.provider.KubernetesCacheUtils;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.description.KubernetesSpinnakerKindMap.SpinnakerKind;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.description.manifest.KubernetesKind;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.description.manifest.KubernetesManifest;
import com.netflix.spinnaker.clouddriver.model.Manifest.Status;
import io.kubernetes.client.models.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.stream.Collectors;

import static com.netflix.spinnaker.clouddriver.kubernetes.v2.description.manifest.KubernetesApiVersion.EXTENSIONS_V1BETA1;
import static com.netflix.spinnaker.clouddriver.kubernetes.v2.description.manifest.KubernetesKind.INGRESS;
import static com.netflix.spinnaker.clouddriver.kubernetes.v2.description.manifest.KubernetesKind.SERVICE;
import static com.netflix.spinnaker.clouddriver.kubernetes.v2.op.handler.KubernetesHandler.DeployPriority.NETWORK_RESOURCE_PRIORITY;

@Component
@Slf4j
public class KubernetesIngressHandler extends KubernetesHandler {
  @Override
  public int deployPriority() {
    return NETWORK_RESOURCE_PRIORITY.getValue();
  }

  @Override
  public KubernetesKind kind() {
    return KubernetesKind.INGRESS;
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
  public Class<? extends KubernetesV2CachingAgent> cachingAgentClass() {
    return KubernetesCoreCachingAgent.class;
  }

  @Override
  public void addRelationships(Map<KubernetesKind, List<KubernetesManifest>> allResources, Map<KubernetesManifest, List<KubernetesManifest>> relationshipMap) {
    Map<KubernetesManifest, List<KubernetesManifest>> result;

    BiFunction<String, String, String> manifestName = (namespace, name) -> namespace + ":" + name;

    Map<String, KubernetesManifest> services = allResources.getOrDefault(SERVICE, new ArrayList<>())
        .stream()
        .collect(Collectors.toMap((m) -> manifestName.apply(m.getNamespace(), m.getName()), (m) -> m));

    for (KubernetesManifest ingress : allResources.getOrDefault(INGRESS, new ArrayList<>())) {
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

      relationshipMap.put(ingress, attachedServices);
    }
  }

  public static List<String> attachedServices(KubernetesManifest manifest) {
    if (manifest.getApiVersion().equals(EXTENSIONS_V1BETA1)) {
      V1beta1Ingress v1beta1Ingress = KubernetesCacheDataConverter.getResource(manifest, V1beta1Ingress.class);
      return attachedServices(v1beta1Ingress);
    } else {
      throw new UnsupportedVersionException(manifest);
    }
  }

  private static List<String> attachedServices(V1beta1Ingress ingress) {
    Set<String> result = new HashSet<>();
    V1beta1IngressBackend backend = ingress.getSpec().getBackend();
    if (backend != null) {
      result.add(backend.getServiceName());
    }

    List<V1beta1IngressRule> rules = ingress.getSpec().getRules();
    rules = rules == null ? new ArrayList<>() : rules;
    for (V1beta1IngressRule rule : rules) {
      V1beta1HTTPIngressRuleValue http = rule.getHttp();
      if (http != null) {
        for (V1beta1HTTPIngressPath path : http.getPaths()) {
          backend = path.getBackend();
          if (backend != null) {
            result.add(backend.getServiceName());
          }
        }
      }
    }

    return new ArrayList<>(result);
  }

  @Override
  public Map<String, Object> hydrateSearchResult(Keys.InfrastructureCacheKey key, KubernetesCacheUtils cacheUtils) {
    Map<String, Object> result = super.hydrateSearchResult(key, cacheUtils);
    result.put("loadBalancer", result.get("name"));

    return result;
  }
}
