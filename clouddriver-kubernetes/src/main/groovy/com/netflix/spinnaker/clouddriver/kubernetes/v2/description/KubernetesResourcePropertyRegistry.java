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

package com.netflix.spinnaker.clouddriver.kubernetes.v2.description;

import com.netflix.spinnaker.clouddriver.kubernetes.v2.artifact.KubernetesUnversionedArtifactConverter;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.artifact.KubernetesVersionedArtifactConverter;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.op.deployer.KubernetesDeployer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class KubernetesResourcePropertyRegistry {
  @Autowired
  public KubernetesResourcePropertyRegistry(List<KubernetesDeployer> deployers,
      KubernetesSpinnakerKindMap kindMap,
      KubernetesVersionedArtifactConverter versionedArtifactConverter,
      KubernetesUnversionedArtifactConverter unversionedArtifactConverter) {
    for (KubernetesDeployer deployer : deployers) {
      KubernetesResourceProperties properties = KubernetesResourceProperties.builder()
          .deployer(deployer)
          .converter(deployer.versioned() ? versionedArtifactConverter : unversionedArtifactConverter)
          .build();

      kindMap.addRelationship(deployer.spinnakerKind(), deployer.kind());
      apiVersionLookup.withApiVersion(deployer.apiVersion()).setProperties(deployer.kind(), properties);
    }
  }

  public ApiVersionLookup lookup() {
    return apiVersionLookup;
  }

  private ApiVersionLookup apiVersionLookup = new ApiVersionLookup();

  public static class KindLookup {
    private ConcurrentHashMap<KubernetesKind, KubernetesResourceProperties> map = new ConcurrentHashMap<>();

    public KubernetesResourceProperties withKind(KubernetesKind kind) {
      if (!map.containsKey(kind)) {
        throw new IllegalArgumentException("No resource properties registered for " + kind);
      } else {
        return map.get(kind);
      }
    }

    public void setProperties(KubernetesKind kind, KubernetesResourceProperties properties) {
      map.put(kind, properties);
    }
  }

  public static class ApiVersionLookup {
    private ConcurrentHashMap<KubernetesApiVersion, KindLookup> map = new ConcurrentHashMap<>();

    public KindLookup withApiVersion(KubernetesApiVersion apiVersion) {
      if (!map.containsKey(apiVersion)) {
        KindLookup result = new KindLookup();
        map.put(apiVersion, result);
        return result;
      } else {
        return map.get(apiVersion);
      }
    }
  }
}
