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
import java.time.OffsetDateTime;
import java.util.*;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import lombok.Getter;
import org.apache.commons.lang3.NotImplementedException;
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
                  OffsetDateTime dtDefault = OffsetDateTime.now();
                  OffsetDateTime time1 =
                      p1.getStatus() != null
                          ? Optional.ofNullable(p1.getStatus().getStartTime()).orElse(dtDefault)
                          : dtDefault;
                  OffsetDateTime time2 =
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
              String logContents;
              try {
                logContents =
                    credentials.jobLogs(location, job.getMetadata().getName(), containerName);
              } catch (Exception jobLogsException) {
                log.error(
                    "Failed to get logs from job: {}, container: {} in namespace: {} for account: {}. Error: ",
                    id,
                    containerName,
                    location,
                    account,
                    jobLogsException);
                return null;
              }
              try {
                if (logContents != null) {
                  return PropertyParser.extractPropertiesFromLog(logContents);
                }
              } catch (Exception e) {
                log.error(
                    "Couldn't parse properties for job: {}, container: {} in namespace: {} for account: {}. Error: ",
                    id,
                    containerName,
                    location,
                    account,
                    e);
              }
              return null;
            })
        .orElse(null);
  }

  /**
   * This method queries a pod for logs, from which it extracts properties which it returns as a map
   * to the caller. This is needed in cases where a pod needs to be queried directly for logs, and
   * getFileContents() doesn't give us all the required information.
   *
   * @param account - account to which the pod belongs
   * @param namespace - namespace in which the pod runs in
   * @param podName - pod to query the logs
   * @param containerName - containerName in the pod from which logs should be queried
   * @return map of property file contents
   */
  @Nullable
  public Map<String, Object> getFileContentsFromPod(
      String account, String namespace, String podName, String containerName) {
    Map<String, Object> props = null;
    String logContents = null;
    KubernetesCredentials credentials =
        (KubernetesCredentials) accountCredentialsProvider.getCredentials(account).getCredentials();
    try {
      logContents = credentials.logs(namespace, podName, containerName);
    } catch (Exception podLogsException) {
      log.error(
          "Failed to get logs from pod: {}, container: {} in namespace: {} for account: {}. Error: ",
          podName,
          containerName,
          namespace,
          account,
          podLogsException);
    }

    try {
      if (logContents != null) {
        props = PropertyParser.extractPropertiesFromLog(logContents);
      }
    } catch (Exception e) {
      log.error(
          "Couldn't parse properties from pod: {}, container: {} in namespace: {} for account: {}. Error: ",
          podName,
          containerName,
          namespace,
          account,
          e);
    }

    return props;
  }

  @Override
  public void cancelJob(String account, String location, String id) {
    throw new NotImplementedException("cancelJob is not implemented for the Kubernetes provider");
  }

  private Optional<V1Job> getKubernetesJob(String account, String location, String id) {
    log.debug("Getting kubernetesJob for account {} at {} with id {}", account, location, id);
    return Optional.ofNullable(manifestProvider.getManifest(account, location, id, false))
        .map(KubernetesManifestContainer::getManifest)
        .map(m -> KubernetesCacheDataConverter.getResource(m, V1Job.class));
  }
}
