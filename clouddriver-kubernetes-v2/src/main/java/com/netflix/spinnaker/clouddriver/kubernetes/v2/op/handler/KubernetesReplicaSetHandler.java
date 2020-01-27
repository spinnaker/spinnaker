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

import static com.netflix.spinnaker.clouddriver.kubernetes.v2.description.manifest.KubernetesApiVersion.APPS_V1;
import static com.netflix.spinnaker.clouddriver.kubernetes.v2.description.manifest.KubernetesApiVersion.APPS_V1BETA2;
import static com.netflix.spinnaker.clouddriver.kubernetes.v2.description.manifest.KubernetesApiVersion.EXTENSIONS_V1BETA1;
import static com.netflix.spinnaker.clouddriver.kubernetes.v2.op.handler.KubernetesHandler.DeployPriority.WORKLOAD_CONTROLLER_PRIORITY;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.netflix.spinnaker.clouddriver.kubernetes.description.SpinnakerKind;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.artifact.Replacer;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.caching.Keys.InfrastructureCacheKey;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.caching.agent.KubernetesCacheDataConverter;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.caching.agent.KubernetesCoreCachingAgent;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.caching.agent.KubernetesV2CachingAgentFactory;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.description.manifest.KubernetesApiVersion;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.description.manifest.KubernetesKind;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.description.manifest.KubernetesManifest;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.description.manifest.KubernetesManifestSelector;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.model.Manifest.Status;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.security.KubernetesV2Credentials;
import io.kubernetes.client.openapi.models.V1ReplicaSet;
import io.kubernetes.client.openapi.models.V1ReplicaSetStatus;
import io.kubernetes.client.openapi.models.V1beta1ReplicaSet;
import io.kubernetes.client.openapi.models.V1beta2ReplicaSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import javax.annotation.Nonnull;
import org.springframework.stereotype.Component;

@Component
public class KubernetesReplicaSetHandler extends KubernetesHandler
    implements CanResize, CanScale, HasPods, ServerGroupHandler {
  private static final ImmutableSet<KubernetesApiVersion> SUPPORTED_API_VERSIONS =
      ImmutableSet.of(EXTENSIONS_V1BETA1, APPS_V1BETA2, APPS_V1);

  @Nonnull
  @Override
  protected ImmutableList<Replacer> artifactReplacers() {
    return ImmutableList.of(
        Replacer.dockerImage(),
        Replacer.configMapVolume(),
        Replacer.secretVolume(),
        Replacer.configMapEnv(),
        Replacer.secretEnv(),
        Replacer.configMapKeyValue(),
        Replacer.secretKeyValue());
  }

  @Override
  public int deployPriority() {
    return WORKLOAD_CONTROLLER_PRIORITY.getValue();
  }

  @Nonnull
  @Override
  public KubernetesKind kind() {
    return KubernetesKind.REPLICA_SET;
  }

  @Override
  public boolean versioned() {
    return true;
  }

  @Nonnull
  @Override
  public SpinnakerKind spinnakerKind() {
    return SpinnakerKind.SERVER_GROUPS;
  }

  @Override
  protected KubernetesV2CachingAgentFactory cachingAgentFactory() {
    return KubernetesCoreCachingAgent::new;
  }

  @Override
  public Status status(KubernetesManifest manifest) {
    if (!SUPPORTED_API_VERSIONS.contains(manifest.getApiVersion())) {
      throw new UnsupportedVersionException(manifest);
    }
    V1ReplicaSet v1ReplicaSet =
        KubernetesCacheDataConverter.getResource(manifest, V1ReplicaSet.class);
    return status(v1ReplicaSet);
  }

  private Status status(V1ReplicaSet replicaSet) {
    Status result = new Status();
    V1ReplicaSetStatus status = replicaSet.getStatus();
    if (status == null) {
      result.unstable("No status reported yet").unavailable("No availability reported");
      return result;
    }

    Long observedGeneration = status.getObservedGeneration();
    if (observedGeneration != null
        && !observedGeneration.equals(replicaSet.getMetadata().getGeneration())) {
      result.unstable("Waiting for replicaset spec update to be observed");
    }

    int desired = replicaSet.getSpec().getReplicas();
    int fullyLabeled = Optional.ofNullable(status.getFullyLabeledReplicas()).orElse(0);
    int available = Optional.ofNullable(status.getAvailableReplicas()).orElse(0);
    int ready = Optional.ofNullable(status.getReadyReplicas()).orElse(0);

    if (desired == 0 && fullyLabeled == 0 && available == 0 && ready == 0) {
      return result;
    }

    if (desired > fullyLabeled) {
      return result
          .unstable("Waiting for all replicas to be fully-labeled")
          .unavailable("Not all replicas have become labeled yet");
    }

    if (desired > available) {
      return result
          .unstable("Waiting for all replicas to be available")
          .unavailable("Not all replicas have become available yet");
    }

    if (desired > ready) {
      return result.unstable("Waiting for all replicas to be ready");
    }

    return result;
  }

  public static Map<String, String> getPodTemplateLabels(KubernetesManifest manifest) {
    if (manifest.getApiVersion().equals(EXTENSIONS_V1BETA1)) {
      V1beta1ReplicaSet v1beta1ReplicaSet =
          KubernetesCacheDataConverter.getResource(manifest, V1beta1ReplicaSet.class);
      return getPodTemplateLabels(v1beta1ReplicaSet);
    } else if (manifest.getApiVersion().equals(APPS_V1BETA2)) {
      V1beta2ReplicaSet v1beta2ReplicaSet =
          KubernetesCacheDataConverter.getResource(manifest, V1beta2ReplicaSet.class);
      return getPodTemplateLabels(v1beta2ReplicaSet);
    } else if (manifest.getApiVersion().equals(APPS_V1)) {
      V1ReplicaSet v1ReplicaSet =
          KubernetesCacheDataConverter.getResource(manifest, V1ReplicaSet.class);
      return getPodTemplateLabels(v1ReplicaSet);
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

  private static Map<String, String> getPodTemplateLabels(V1ReplicaSet replicaSet) {
    return replicaSet.getSpec().getTemplate().getMetadata().getLabels();
  }

  @Override
  public Map<String, Object> hydrateSearchResult(InfrastructureCacheKey key) {
    Map<String, Object> result = super.hydrateSearchResult(key);
    result.put("serverGroup", result.get("name"));

    return result;
  }

  @Override
  public List<KubernetesManifest> pods(
      KubernetesV2Credentials credentials, KubernetesManifest object) {
    KubernetesManifestSelector selector = object.getManifestSelector();
    return credentials.list(KubernetesKind.POD, object.getNamespace(), selector.toSelectorList())
        .stream()
        .filter(
            p ->
                p.getOwnerReferences().stream()
                    .anyMatch(or -> or.getName().equals(object.getName())))
        .collect(Collectors.toList());
  }
}
