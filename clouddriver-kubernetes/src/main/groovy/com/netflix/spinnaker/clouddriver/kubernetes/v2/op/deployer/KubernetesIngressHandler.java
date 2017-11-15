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

package com.netflix.spinnaker.clouddriver.kubernetes.v2.op.deployer;

import com.netflix.spinnaker.clouddriver.kubernetes.v2.caching.agent.KubernetesCacheDataConverter;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.caching.agent.KubernetesIngressCachingAgent;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.caching.agent.KubernetesV2CachingAgent;
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
import java.util.Set;

@Component
public class KubernetesIngressHandler extends KubernetesHandler implements CanDelete {
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
    return Status.stable();
  }

  @Override
  public Class<? extends KubernetesV2CachingAgent> cachingAgentClass() {
    return KubernetesIngressCachingAgent.class;
  }

  public static List<String> attachedServices(KubernetesManifest manifest) {
    switch (manifest.getApiVersion()) {
      case EXTENSIONS_V1BETA1:
        V1beta1Ingress v1beta1Ingress = KubernetesCacheDataConverter.getResource(manifest, V1beta1Ingress.class);
        return attachedServices(v1beta1Ingress);
      default:
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
}
