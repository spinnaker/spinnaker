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
import com.netflix.spinnaker.clouddriver.kubernetes.v2.caching.agent.KubernetesServiceCachingAgent;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.caching.agent.KubernetesV2CachingAgent;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.description.KubernetesSpinnakerKindMap.SpinnakerKind;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.description.manifest.KubernetesKind;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.description.manifest.KubernetesManifest;
import com.netflix.spinnaker.clouddriver.model.Manifest.Status;
import io.kubernetes.client.models.V1Service;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class KubernetesServiceHandler extends KubernetesHandler implements CanDelete {
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
    return SpinnakerKind.LOAD_BALANCER;
  }

  @Override
  public Status status(KubernetesManifest manifest) {
    return Status.stable();
  }

  @Override
  public Class<? extends KubernetesV2CachingAgent> cachingAgentClass() {
    return KubernetesServiceCachingAgent.class;
  }

  public static Map<String, String> getSelector(KubernetesManifest manifest) {
    switch (manifest.getApiVersion()) {
      case V1:
        V1Service v1Service = KubernetesCacheDataConverter.getResource(manifest, V1Service.class);
        return v1Service.getSpec().getSelector();
      default:
        throw new IllegalArgumentException("No services with version " + manifest.getApiVersion() + " supported");
    }
  }
}
