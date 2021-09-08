/*
 * Copyright 2019 Armory
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

package com.netflix.spinnaker.clouddriver.kubernetes.provider.view;

import com.netflix.spinnaker.clouddriver.kubernetes.caching.agent.KubernetesCacheDataConverter;
import com.netflix.spinnaker.clouddriver.kubernetes.caching.view.model.KubernetesManifestContainer;
import com.netflix.spinnaker.clouddriver.kubernetes.caching.view.provider.KubernetesManifestProvider;
import com.netflix.spinnaker.clouddriver.kubernetes.description.manifest.KubernetesKind;
import com.netflix.spinnaker.clouddriver.kubernetes.description.manifest.KubernetesManifest;
import com.netflix.spinnaker.clouddriver.kubernetes.model.KubernetesJobStatus;
import com.netflix.spinnaker.clouddriver.kubernetes.security.KubernetesCredentials;
import com.netflix.spinnaker.clouddriver.kubernetes.security.KubernetesSelectorList;
import com.netflix.spinnaker.clouddriver.model.JobProvider;
import com.netflix.spinnaker.clouddriver.model.JobState;
import com.netflix.spinnaker.clouddriver.security.AccountCredentialsProvider;
import io.kubernetes.client.openapi.models.V1Job;
import io.kubernetes.client.openapi.models.V1Pod;
import java.util.*;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import lombok.Getter;
import org.apache.commons.lang3.NotImplementedException;
import org.joda.time.DateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class KubernetesJobProvider implements JobProvider<KubernetesJobStatus> {
  private static final Logger log = LoggerFactory.getLogger(KubernetesJobProvider.class);
  @Getter private final String platform = "kubernetes";
  private final AccountCredentialsProvider accountCredentialsProvider;
  private final KubernetesManifestProvider manifestProvider;
  private final boolean detailedPodStatus;

  KubernetesJobProvider(
      AccountCredentialsProvider accountCredentialsProvider,
      KubernetesManifestProvider manifestProvider,
      @Value("${kubernetes.jobs.detailed-pod-status:true}") boolean detailedPodStatus) {
    this.accountCredentialsProvider = accountCredentialsProvider;
    this.manifestProvider = manifestProvider;
    this.detailedPodStatus = detailedPodStatus;
  }

  @Override
  @Nullable
  public KubernetesJobStatus collectJob(String account, String location, String id) {
    Optional<V1Job> optionalJob = getKubernetesJob(account, location, id);
    if (!optionalJob.isPresent()) {
      return null;
    }
    V1Job job = optionalJob.get();
    KubernetesJobStatus jobStatus = new KubernetesJobStatus(job, account);
    KubernetesCredentials credentials =
        (KubernetesCredentials) accountCredentialsProvider.getCredentials(account).getCredentials();

    Map<String, String> selector = job.getSpec().getSelector().getMatchLabels();
    List<KubernetesManifest> pods =
        credentials.list(
            KubernetesKind.POD,
            jobStatus.getLocation(),
            KubernetesSelectorList.fromMatchLabels(selector));

    List<V1Pod> typedPods =
        pods.stream()
            .map(m -> KubernetesCacheDataConverter.getResource(m, V1Pod.class))
            .sorted(
                (p1, p2) -> {
                  DateTime dtDefault = new DateTime(0);
                  DateTime time1 =
                      p1.getStatus() != null
                          ? Optional.ofNullable(p1.getStatus().getStartTime()).orElse(dtDefault)
                          : dtDefault;
                  DateTime time2 =
                      p2.getStatus() != null
                          ? Optional.ofNullable(p2.getStatus().getStartTime()).orElse(dtDefault)
                          : dtDefault;
                  return time1.compareTo(time2);
                })
            .collect(Collectors.toList());

    V1Pod mostRecentPod = typedPods.get(typedPods.size() - 1);
    jobStatus.setMostRecentPodName(
        mostRecentPod.getMetadata() != null ? mostRecentPod.getMetadata().getName() : "");

    jobStatus.setPods(
        typedPods.stream().map(KubernetesJobStatus.PodStatus::new).collect(Collectors.toList()));

    if (jobStatus.getJobState() == JobState.Failed) {
      jobStatus.captureFailureDetails();
    }

    // if detailedPodStatus is not needed, then remove all the pod related information
    if (!detailedPodStatus) {
      jobStatus.setPods(List.of());
    }

    return jobStatus;
  }

  @Override
  @Nullable
  public Map<String, Object> getFileContents(
      String account, String location, String id, String containerName) {
    KubernetesCredentials credentials =
        (KubernetesCredentials) accountCredentialsProvider.getCredentials(account).getCredentials();
    return getKubernetesJob(account, location, id)
        .map(
            job -> {
              try {
                String logContents =
                    credentials.jobLogs(location, job.getMetadata().getName(), containerName);
                return PropertyParser.extractPropertiesFromLog(logContents);
              } catch (Exception e) {
                log.error("Couldn't parse properties for account {} at {}", account, location);
                return null;
              }
            })
        .orElse(null);
  }

  @Override
  public void cancelJob(String account, String location, String id) {
    throw new NotImplementedException("cancelJob is not implemented for the Kubernetes provider");
  }

  private Optional<V1Job> getKubernetesJob(String account, String location, String id) {
    return Optional.ofNullable(manifestProvider.getManifest(account, location, id, false))
        .map(KubernetesManifestContainer::getManifest)
        .map(m -> KubernetesCacheDataConverter.getResource(m, V1Job.class));
  }
}
