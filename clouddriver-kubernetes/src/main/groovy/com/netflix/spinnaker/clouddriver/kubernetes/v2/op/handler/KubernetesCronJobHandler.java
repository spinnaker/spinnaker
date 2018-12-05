/*
 * Copyright 2018 Google, Inc.
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
import com.netflix.spinnaker.clouddriver.kubernetes.v2.caching.agent.KubernetesV2CachingAgent;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.description.KubernetesSpinnakerKindMap;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.description.manifest.KubernetesKind;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.description.manifest.KubernetesManifest;
import com.netflix.spinnaker.clouddriver.model.Manifest.Status;
import io.kubernetes.client.models.V2alpha1CronJob;
import io.kubernetes.client.models.V2alpha1CronJobStatus;
import org.springframework.stereotype.Component;

import static com.netflix.spinnaker.clouddriver.kubernetes.v2.op.handler.KubernetesHandler.DeployPriority.WORKLOAD_CONTROLLER_PRIORITY;

@Component
public class KubernetesCronJobHandler extends KubernetesHandler implements
  CanDelete,
  ServerGroupHandler {

  public KubernetesCronJobHandler() {
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
    return KubernetesKind.CRON_JOB;
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
    V2alpha1CronJob v2alpha1CronJob = KubernetesCacheDataConverter.getResource(manifest, V2alpha1CronJob.class);
    return status(v2alpha1CronJob);
  }

  @Override
  public Class<? extends KubernetesV2CachingAgent> cachingAgentClass() {
    return KubernetesCoreCachingAgent.class;
  }

  private Status status(V2alpha1CronJob job) {
    Status result = new Status();
    V2alpha1CronJobStatus status = job.getStatus();
    if (status == null) {
      result.unstable("No status reported yet")
          .unavailable("No availability reported");
      return result;
    }

    return result;
  }

}
