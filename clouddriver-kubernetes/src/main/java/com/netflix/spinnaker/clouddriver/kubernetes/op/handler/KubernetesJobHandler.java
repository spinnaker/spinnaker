/*
 * Copyright 2018 Joel Wilsson
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

import static com.netflix.spinnaker.clouddriver.kubernetes.op.handler.KubernetesHandler.DeployPriority.WORKLOAD_CONTROLLER_PRIORITY;

import com.google.common.collect.ImmutableList;
import com.netflix.spinnaker.clouddriver.kubernetes.artifact.Replacer;
import com.netflix.spinnaker.clouddriver.kubernetes.caching.agent.KubernetesCacheDataConverter;
import com.netflix.spinnaker.clouddriver.kubernetes.caching.agent.KubernetesCachingAgentFactory;
import com.netflix.spinnaker.clouddriver.kubernetes.caching.agent.KubernetesCoreCachingAgent;
import com.netflix.spinnaker.clouddriver.kubernetes.description.SpinnakerKind;
import com.netflix.spinnaker.clouddriver.kubernetes.description.manifest.KubernetesKind;
import com.netflix.spinnaker.clouddriver.kubernetes.description.manifest.KubernetesManifest;
import com.netflix.spinnaker.clouddriver.kubernetes.model.Manifest.Status;
import io.kubernetes.client.openapi.models.V1Job;
import io.kubernetes.client.openapi.models.V1JobCondition;
import io.kubernetes.client.openapi.models.V1JobSpec;
import io.kubernetes.client.openapi.models.V1JobStatus;
import java.util.List;
import java.util.Optional;
import javax.annotation.Nonnull;
import org.springframework.stereotype.Component;

@Component
public class KubernetesJobHandler extends KubernetesHandler implements ServerGroupHandler {
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
    return KubernetesKind.JOB;
  }

  @Override
  public boolean versioned() {
    return false;
  }

  @Nonnull
  @Override
  public SpinnakerKind spinnakerKind() {
    return SpinnakerKind.SERVER_GROUPS;
  }

  @Override
  public Status status(KubernetesManifest manifest) {
    V1Job v1Job = KubernetesCacheDataConverter.getResource(manifest, V1Job.class);
    return status(v1Job);
  }

  @Override
  protected KubernetesCachingAgentFactory cachingAgentFactory() {
    return KubernetesCoreCachingAgent::new;
  }

  private Status status(V1Job job) {
    V1JobStatus status = job.getStatus();
    if (status == null) {
      return Status.noneReported();
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
      conditions = conditions != null ? conditions : ImmutableList.of();
      Optional<V1JobCondition> condition = conditions.stream().filter(this::jobFailed).findAny();
      return condition
          .map(v1JobCondition -> Status.defaultStatus().failed(v1JobCondition.getMessage()))
          .orElseGet(() -> Status.defaultStatus().unstable("Waiting for jobs to finish"));
    }

    return Status.defaultStatus();
  }

  private boolean jobFailed(V1JobCondition condition) {
    return "Failed".equalsIgnoreCase(condition.getType())
        && "True".equalsIgnoreCase(condition.getStatus());
  }
}
