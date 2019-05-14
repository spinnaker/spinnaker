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

import static com.netflix.spinnaker.clouddriver.kubernetes.v2.description.manifest.KubernetesKind.SERVICE;
import static com.netflix.spinnaker.clouddriver.kubernetes.v2.description.manifest.KubernetesKind.STATEFUL_SET;
import static com.netflix.spinnaker.clouddriver.kubernetes.v2.op.handler.KubernetesHandler.DeployPriority.WORKLOAD_CONTROLLER_PRIORITY;

import com.netflix.spinnaker.clouddriver.kubernetes.v2.artifact.ArtifactReplacerFactory;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.caching.Keys;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.caching.agent.KubernetesCacheDataConverter;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.caching.agent.KubernetesCoreCachingAgent;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.caching.agent.KubernetesV2CachingAgentFactory;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.caching.view.provider.KubernetesCacheUtils;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.description.KubernetesSpinnakerKindMap.SpinnakerKind;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.description.manifest.KubernetesKind;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.description.manifest.KubernetesManifest;
import com.netflix.spinnaker.clouddriver.model.Manifest.Status;
import io.kubernetes.client.models.V1beta2RollingUpdateStatefulSetStrategy;
import io.kubernetes.client.models.V1beta2StatefulSet;
import io.kubernetes.client.models.V1beta2StatefulSetStatus;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.stream.Collectors;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

@Component
public class KubernetesStatefulSetHandler extends KubernetesHandler
    implements CanResize,
        CanScale,
        CanPauseRollout,
        CanResumeRollout,
        CanUndoRollout,
        ServerGroupHandler {

  public KubernetesStatefulSetHandler() {
    registerReplacer(ArtifactReplacerFactory.dockerImageReplacer());
    registerReplacer(ArtifactReplacerFactory.configMapVolumeReplacer());
    registerReplacer(ArtifactReplacerFactory.secretVolumeReplacer());
    registerReplacer(ArtifactReplacerFactory.configMapEnvFromReplacer());
    registerReplacer(ArtifactReplacerFactory.secretEnvFromReplacer());
    registerReplacer(ArtifactReplacerFactory.configMapKeyValueFromReplacer());
    registerReplacer(ArtifactReplacerFactory.secretKeyValueFromReplacer());
  }

  @Override
  public int deployPriority() {
    return WORKLOAD_CONTROLLER_PRIORITY.getValue();
  }

  @Override
  public KubernetesKind kind() {
    return STATEFUL_SET;
  }

  @Override
  public boolean versioned() {
    return false;
  }

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
    if (manifest.isNewerThanObservedGeneration()) {
      return (new Status()).unknown();
    }
    V1beta2StatefulSet v1beta2StatefulSet =
        KubernetesCacheDataConverter.getResource(manifest, V1beta2StatefulSet.class);
    return status(v1beta2StatefulSet);
  }

  public static String serviceName(KubernetesManifest manifest) {
    // TODO(lwander) perhaps switch on API version if this changes
    Map<String, Object> spec = (Map<String, Object>) manifest.get("spec");
    return (String) spec.get("serviceName");
  }

  @Override
  public Map<String, Object> hydrateSearchResult(
      Keys.InfrastructureCacheKey key, KubernetesCacheUtils cacheUtils) {
    Map<String, Object> result = super.hydrateSearchResult(key, cacheUtils);
    result.put("serverGroup", result.get("name"));

    return result;
  }

  private Status status(V1beta2StatefulSet statefulSet) {
    Status result = new Status();

    if (statefulSet.getSpec().getUpdateStrategy().getType().equalsIgnoreCase("ondelete")) {
      return result;
    }

    V1beta2StatefulSetStatus status = statefulSet.getStatus();
    if (status == null) {
      result.unstable("No status reported yet").unavailable("No availability reported");
      return result;
    }

    Integer desiredReplicas = statefulSet.getSpec().getReplicas();
    Integer existing = status.getReplicas();
    if (existing == null || (desiredReplicas != null && desiredReplicas > existing)) {
      return result.unstable("Waiting for at least the desired replica count to be met");
    }

    existing = status.getReadyReplicas();
    if (existing == null || (desiredReplicas != null && desiredReplicas > existing)) {
      return result.unstable("Waiting for all updated replicas to be ready");
    }

    String updateType = statefulSet.getSpec().getUpdateStrategy().getType();
    V1beta2RollingUpdateStatefulSetStrategy rollingUpdate =
        statefulSet.getSpec().getUpdateStrategy().getRollingUpdate();

    Integer updated = status.getUpdatedReplicas();

    if (updateType.equalsIgnoreCase("rollingupdate") && updated != null && rollingUpdate != null) {
      Integer partition = rollingUpdate.getPartition();
      Integer replicas = status.getReplicas();
      if (replicas != null && partition != null && (updated < (replicas - partition))) {
        return result.unstable("Waiting for partitioned roll out to finish");
      }
      result.setStable(new Status.Condition(true, "Partitioned roll out complete"));
      return result;
    }

    existing = status.getCurrentReplicas();
    if (existing == null || (desiredReplicas != null && desiredReplicas > existing)) {
      return result.unstable("Waiting for all updated replicas to be scheduled");
    }

    if (!status.getCurrentRevision().equals(status.getUpdateRevision())) {
      return result.unstable("Waiting for the updated revision to match the current revision");
    }

    return result;
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

    for (KubernetesManifest manifest : allResources.getOrDefault(STATEFUL_SET, new ArrayList<>())) {
      String serviceName = KubernetesStatefulSetHandler.serviceName(manifest);
      if (StringUtils.isEmpty(serviceName)) {
        continue;
      }

      String key = manifestName.apply(manifest.getNamespace(), serviceName);

      if (!services.containsKey(key)) {
        continue;
      }

      KubernetesManifest service = services.get(key);
      relationshipMap.put(manifest, Collections.singletonList(service));
    }
  }
}
