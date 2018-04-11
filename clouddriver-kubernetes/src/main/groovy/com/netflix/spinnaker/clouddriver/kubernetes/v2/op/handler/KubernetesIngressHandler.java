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
import com.netflix.spinnaker.clouddriver.kubernetes.v2.caching.agent.KubernetesIngressCachingAgent;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.caching.agent.KubernetesV2CachingAgent;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.caching.view.provider.KubernetesCacheUtils;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.description.KubernetesSpinnakerKindMap.SpinnakerKind;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.description.manifest.KubernetesKind;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.description.manifest.KubernetesManifest;
import com.netflix.spinnaker.clouddriver.model.Manifest.Status;
import io.kubernetes.client.models.V1beta1HTTPIngressPath;
import io.kubernetes.client.models.V1beta1Ingress;
import io.kubernetes.client.models.V1beta1IngressBackend;
import io.kubernetes.client.models.V1beta1IngressRule;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.netflix.spinnaker.clouddriver.kubernetes.v2.description.manifest.KubernetesApiVersion.EXTENSIONS_V1BETA1;
import static com.netflix.spinnaker.clouddriver.kubernetes.v2.op.handler.KubernetesHandler.DeployPriority.NETWORK_RESOURCE_PRIORITY;

@Component
public class KubernetesIngressHandler extends KubernetesHandler implements CanDelete {
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
    return KubernetesIngressCachingAgent.class;
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
      for (V1beta1HTTPIngressPath path : rule.getHttp().getPaths()) {
        backend = path.getBackend();
        if (backend != null) {
          result.add(backend.getServiceName());
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
