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

import static com.netflix.spinnaker.clouddriver.kubernetes.description.manifest.KubernetesApiVersion.EXTENSIONS_V1BETA1;
import static com.netflix.spinnaker.clouddriver.kubernetes.description.manifest.KubernetesApiVersion.NETWORKING_K8S_IO_V1BETA1;
import static com.netflix.spinnaker.clouddriver.kubernetes.description.manifest.KubernetesKind.INGRESS;
import static com.netflix.spinnaker.clouddriver.kubernetes.description.manifest.KubernetesKind.SERVICE;
import static com.netflix.spinnaker.clouddriver.kubernetes.op.handler.KubernetesHandler.DeployPriority.NETWORK_RESOURCE_PRIORITY;

import com.google.common.collect.ImmutableSet;
import com.netflix.spinnaker.clouddriver.kubernetes.caching.Keys.InfrastructureCacheKey;
import com.netflix.spinnaker.clouddriver.kubernetes.caching.agent.KubernetesCacheDataConverter;
import com.netflix.spinnaker.clouddriver.kubernetes.caching.agent.KubernetesCachingAgentFactory;
import com.netflix.spinnaker.clouddriver.kubernetes.caching.agent.KubernetesCoreCachingAgent;
import com.netflix.spinnaker.clouddriver.kubernetes.description.SpinnakerKind;
import com.netflix.spinnaker.clouddriver.kubernetes.description.manifest.KubernetesApiVersion;
import com.netflix.spinnaker.clouddriver.kubernetes.description.manifest.KubernetesKind;
import com.netflix.spinnaker.clouddriver.kubernetes.description.manifest.KubernetesManifest;
import com.netflix.spinnaker.clouddriver.kubernetes.model.Manifest.Status;
import io.kubernetes.client.openapi.models.NetworkingV1beta1HTTPIngressPath;
import io.kubernetes.client.openapi.models.NetworkingV1beta1HTTPIngressRuleValue;
import io.kubernetes.client.openapi.models.NetworkingV1beta1Ingress;
import io.kubernetes.client.openapi.models.NetworkingV1beta1IngressBackend;
import io.kubernetes.client.openapi.models.NetworkingV1beta1IngressRule;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.stream.Collectors;
import javax.annotation.Nonnull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class KubernetesIngressHandler extends KubernetesHandler {
  private static final Logger log = LoggerFactory.getLogger(KubernetesIngressHandler.class);
  private static final ImmutableSet<KubernetesApiVersion> SUPPORTED_API_VERSIONS =
      ImmutableSet.of(EXTENSIONS_V1BETA1, NETWORKING_K8S_IO_V1BETA1);

  @Override
  public int deployPriority() {
    return NETWORK_RESOURCE_PRIORITY.getValue();
  }

  @Nonnull
  @Override
  public KubernetesKind kind() {
    return KubernetesKind.INGRESS;
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
  public void addRelationships(
      Map<KubernetesKind, List<KubernetesManifest>> allResources,
      Map<KubernetesManifest, List<KubernetesManifest>> relationshipMap) {
    BiFunction<String, String, String> manifestName = (namespace, name) -> namespace + ":" + name;

    Map<String, KubernetesManifest> services =
        allResources.getOrDefault(SERVICE, new ArrayList<>()).stream()
            .collect(
                Collectors.toMap(
                    (m) -> manifestName.apply(m.getNamespace(), m.getName()), (m) -> m));

    for (KubernetesManifest ingress : allResources.getOrDefault(INGRESS, new ArrayList<>())) {
      List<KubernetesManifest> attachedServices = new ArrayList<>();
      try {
        attachedServices =
            KubernetesIngressHandler.attachedServices(ingress).stream()
                .map(s -> services.get(manifestName.apply(ingress.getNamespace(), s)))
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
      } catch (Exception e) {
        log.warn("Failure getting services attached to {}", ingress.getName(), e);
      }

      relationshipMap.put(ingress, attachedServices);
    }
  }

  private static List<String> attachedServices(KubernetesManifest manifest) {
    if (!SUPPORTED_API_VERSIONS.contains(manifest.getApiVersion())) {
      throw new UnsupportedVersionException(manifest);
    }
    NetworkingV1beta1Ingress v1beta1Ingress =
        KubernetesCacheDataConverter.getResource(manifest, NetworkingV1beta1Ingress.class);
    return attachedServices(v1beta1Ingress);
  }

  private static List<String> attachedServices(NetworkingV1beta1Ingress ingress) {
    Set<String> result = new HashSet<>();
    NetworkingV1beta1IngressBackend backend = ingress.getSpec().getBackend();
    if (backend != null) {
      result.add(backend.getServiceName());
    }

    List<NetworkingV1beta1IngressRule> rules = ingress.getSpec().getRules();
    rules = rules == null ? new ArrayList<>() : rules;
    for (NetworkingV1beta1IngressRule rule : rules) {
      NetworkingV1beta1HTTPIngressRuleValue http = rule.getHttp();
      if (http != null) {
        for (NetworkingV1beta1HTTPIngressPath path : http.getPaths()) {
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
  public Map<String, Object> hydrateSearchResult(InfrastructureCacheKey key) {
    Map<String, Object> result = super.hydrateSearchResult(key);
    result.put("loadBalancer", result.get("name"));

    return result;
  }
}
