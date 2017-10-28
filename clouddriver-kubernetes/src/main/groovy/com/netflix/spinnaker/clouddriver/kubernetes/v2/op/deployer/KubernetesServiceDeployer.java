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
import com.netflix.spinnaker.clouddriver.kubernetes.v2.description.KubernetesSpinnakerKindMap.SpinnakerKind;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.description.manifest.KubernetesKind;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.description.manifest.KubernetesManifest;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.security.KubernetesV2Credentials;
import io.kubernetes.client.models.V1Service;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class KubernetesServiceDeployer extends KubernetesDeployer implements CanDelete<Void> {
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
  public boolean isStable(KubernetesManifest manifest) {
    return false;
  }

  @Override
  public Class<Void> getDeleteOptionsClass() {
    return Void.class;
  }

  @Override
  public void delete(KubernetesV2Credentials credentials, String namespace, String name, Void deleteOptions) {
    credentials.deleteService(namespace, name);
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
