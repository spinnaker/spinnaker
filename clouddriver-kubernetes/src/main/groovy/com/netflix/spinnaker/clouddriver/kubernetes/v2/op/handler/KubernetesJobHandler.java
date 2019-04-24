/*
 * Copyright 2018 Joel Wilsson
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

import com.netflix.spinnaker.clouddriver.kubernetes.v2.artifact.ArtifactReplacerFactory;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.caching.agent.KubernetesCacheDataConverter;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.caching.agent.KubernetesCoreCachingAgent;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.caching.agent.KubernetesV2CachingAgentFactory;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.description.KubernetesSpinnakerKindMap;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.description.manifest.KubernetesKind;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.description.manifest.KubernetesManifest;
import com.netflix.spinnaker.clouddriver.model.Manifest.Status;
import io.kubernetes.client.models.V1Job;
import io.kubernetes.client.models.V1JobCondition;
import io.kubernetes.client.models.V1JobSpec;
import io.kubernetes.client.models.V1JobStatus;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static com.netflix.spinnaker.clouddriver.kubernetes.v2.op.handler.KubernetesHandler.DeployPriority.WORKLOAD_CONTROLLER_PRIORITY;

@Component
public class KubernetesJobHandler extends KubernetesHandler implements
  ServerGroupHandler {

  public KubernetesJobHandler() {
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
    return KubernetesKind.JOB;
  }

  @Override
  public boolean versioned() {
    return false;
  }

  @Override
  public KubernetesSpinnakerKindMap.SpinnakerKind spinnakerKind() {
    return KubernetesSpinnakerKindMap.SpinnakerKind.SERVER_GROUPS;
  }

  @Override
  public Status status(KubernetesManifest manifest) {
    V1Job v1Job = KubernetesCacheDataConverter.getResource(manifest, V1Job.class);
    return status(v1Job);
  }

  @Override
  protected KubernetesV2CachingAgentFactory cachingAgentFactory() {
    return KubernetesCoreCachingAgent::new;
  }

  private Status status(V1Job job) {
    Status result = new Status();
    V1JobStatus status = job.getStatus();
    if (status == null) {
      result.unstable("No status reported yet")
          .unavailable("No availability reported");
      return result;
    }

    int completions = 1;
    V1JobSpec spec = job.getSpec();
    if (spec != null && spec.getCompletions() != null) {
      completions = spec.getCompletions();
    }
    int succeeded = 0;
    if (status.getSucceeded() != null) {
      succeeded = status.getSucceeded();
    }

    if (succeeded < completions) {
      List<V1JobCondition> conditions = status.getConditions();
      conditions = conditions != null ? conditions : Collections.emptyList();
      Optional<V1JobCondition> condition = conditions.stream().filter(this::jobFailed).findAny();
      if (condition.isPresent()) {
        return result.failed(condition.get().getMessage());
      } else {
        return result.unstable("Waiting for jobs to finish");
      }
    }

    return result;
  }

  private boolean jobFailed(V1JobCondition condition) {
    return "Failed".equalsIgnoreCase(condition.getType()) && "True".equalsIgnoreCase(condition.getStatus());
  }
}
