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

import static com.netflix.spinnaker.clouddriver.kubernetes.v2.description.manifest.KubernetesApiVersion.APPS_V1BETA2;
import static com.netflix.spinnaker.clouddriver.kubernetes.v2.description.manifest.KubernetesApiVersion.EXTENSIONS_V1BETA1;

import com.netflix.spinnaker.clouddriver.kubernetes.v2.artifact.ArtifactReplacer.Replacer;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.artifact.ArtifactTypes;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.caching.Keys;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.caching.agent.KubernetesCacheDataConverter;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.caching.agent.KubernetesReplicaSetCachingAgent;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.caching.agent.KubernetesV2CachingAgent;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.caching.view.provider.KubernetesCacheUtils;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.description.KubernetesSpinnakerKindMap.SpinnakerKind;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.description.manifest.KubernetesKind;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.description.manifest.KubernetesManifest;
import com.netflix.spinnaker.clouddriver.model.Manifest.Status;
import io.kubernetes.client.models.V1beta1ReplicaSet;
import io.kubernetes.client.models.V1beta2ReplicaSet;
import io.kubernetes.client.models.V1beta2ReplicaSetStatus;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class KubernetesReplicaSetHandler extends KubernetesHandler implements
    CanResize,
    CanDelete,
    CanScale {

  public KubernetesReplicaSetHandler() {
    registerReplacer(
        Replacer.builder()
            .replacePath("$.spec.template.spec.containers.[?( @.image == \"{%name%}\" )].image")
            .findPath("$.spec.template.spec.containers.*.image")
            .type(ArtifactTypes.DOCKER_IMAGE)
            .build()
    );
    registerReplacer(
      Replacer.builder()
        .replacePath("$.spec.template.spec.volumes.[?( @.configMap.name == \"{%name%}\" )].configMap.name")
        .findPath("$.spec.template.spec.volumes.*.configMap.name")
        .type(ArtifactTypes.KUBERNETES_CONFIG_MAP)
        .build()
    );
  }

  @Override
  public KubernetesKind kind() {
    return KubernetesKind.REPLICA_SET;
  }

  @Override
  public boolean versioned() {
    return true;
  }

  @Override
  public SpinnakerKind spinnakerKind() {
    return SpinnakerKind.SERVER_GROUPS;
  }

  @Override
  public Class<? extends KubernetesV2CachingAgent> cachingAgentClass() {
    return KubernetesReplicaSetCachingAgent.class;
  }

  @Override
  public Status status(KubernetesManifest manifest) {
    if (manifest.getApiVersion().equals(EXTENSIONS_V1BETA1) || manifest.getApiVersion().equals(APPS_V1BETA2)) {
      V1beta2ReplicaSet v1beta2ReplicaSet = KubernetesCacheDataConverter.getResource(manifest, V1beta2ReplicaSet.class);
      return status(v1beta2ReplicaSet);
    } else {
      throw new UnsupportedVersionException(manifest);
    }
  }

  private Status status(V1beta2ReplicaSet replicaSet) {
    Status result = new Status();
    int desiredReplicas = replicaSet.getSpec().getReplicas();
    V1beta2ReplicaSetStatus status = replicaSet.getStatus();
    if (status == null) {
      result.unstable("No status reported yet")
          .unavailable("No availability reported");
      return result;
    }

    Integer existing = status.getFullyLabeledReplicas();
    if (existing == null || desiredReplicas > existing) {
      return result.unstable("Waiting for all replicas to be fully-labeled")
          .unavailable("Not all replicas have become labeled yet");
    }

    existing = status.getAvailableReplicas();
    if (existing == null || desiredReplicas > existing) {
      return result.unstable("Waiting for all replicas to be available")
          .unavailable("Not all replicas have become available yet");
    }

    existing = status.getReadyReplicas();
    if (existing == null || desiredReplicas > existing) {
      return result.unstable("Waiting for all replicas to be ready");
    }

    return result;
  }

  public static Map<String, String> getPodTemplateLabels(KubernetesManifest manifest) {
    if (manifest.getApiVersion().equals(EXTENSIONS_V1BETA1)) {
      V1beta1ReplicaSet v1beta1ReplicaSet = KubernetesCacheDataConverter.getResource(manifest, V1beta1ReplicaSet.class);
      return getPodTemplateLabels(v1beta1ReplicaSet);
    } else if (manifest.getApiVersion().equals(APPS_V1BETA2)) {
      V1beta2ReplicaSet v1beta2ReplicaSet = KubernetesCacheDataConverter.getResource(manifest, V1beta2ReplicaSet.class);
      return getPodTemplateLabels(v1beta2ReplicaSet);
    } else {
      throw new UnsupportedVersionException(manifest);
    }
  }

  private static Map<String, String> getPodTemplateLabels(V1beta1ReplicaSet replicaSet) {
    return replicaSet.getSpec().getTemplate().getMetadata().getLabels();
  }

  private static Map<String, String> getPodTemplateLabels(V1beta2ReplicaSet replicaSet) {
    return replicaSet.getSpec().getTemplate().getMetadata().getLabels();
  }

  @Override
  public Map<String, Object> hydrateSearchResult(Keys.InfrastructureCacheKey key, KubernetesCacheUtils cacheUtils) {
    Map<String, Object> result = super.hydrateSearchResult(key, cacheUtils);
    result.put("serverGroup", result.get("name"));

    return result;
  }
}
