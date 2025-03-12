/*
 * Copyright 2021 Salesforce.com, Inc.
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

package com.netflix.spinnaker.clouddriver.kubernetes.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.spy;

import com.google.common.collect.ImmutableList;
import com.google.common.io.Resources;
import com.netflix.spinnaker.clouddriver.kubernetes.caching.agent.KubernetesCacheDataConverter;
import com.netflix.spinnaker.clouddriver.kubernetes.description.manifest.KubernetesManifest;
import io.kubernetes.client.openapi.models.V1Job;
import io.kubernetes.client.openapi.models.V1Pod;
import io.kubernetes.client.util.Yaml;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class KubernetesJobStatusTest {
  @Test
  void testCompletionDetailsForSuccessfulJobCompletion() {
    // setup
    KubernetesJobStatus kubernetesJobStatus = spy(getKubernetesJobStatus("successful-job.yml"));

    // when
    kubernetesJobStatus.captureFailureDetails();

    // then
    Map<String, String> result = kubernetesJobStatus.getCompletionDetails();
    assertFalse(result.isEmpty());
    assertThat(result.get("message")).isNullOrEmpty();
    assertThat(result.get("exitCode")).isNullOrEmpty();
    assertThat(result.get("signal")).isNullOrEmpty();
    assertThat(result.get("reason")).isNullOrEmpty();

    // we shouldn't see any failure details if the job was successful
    assertNull(kubernetesJobStatus.getFailureDetails());
  }

  @Test
  void testJobFailureDetailsForJobWithSinglePodWithFailedInitContainers() {
    // setup
    KubernetesJobStatus jobStatus = getKubernetesJobStatus("failed-job-init-container-error.yml");

    // when
    jobStatus.captureFailureDetails();

    // then
    assertThat(jobStatus.getMessage()).isEqualTo("Job has reached the specified backoff limit");
    assertThat(jobStatus.getReason()).isEqualTo("BackoffLimitExceeded");
    assertThat(jobStatus.getPods().size()).isEqualTo(1);
    KubernetesJobStatus.PodStatus podStatus = jobStatus.getPods().get(0);
    assertThat(podStatus.getContainerExecutionDetails().size()).isEqualTo(2);
    Optional<KubernetesJobStatus.ContainerExecutionDetails> failedContainer =
        podStatus.getContainerExecutionDetails().stream()
            .filter(c -> c.getName().equals("init-myservice"))
            .findFirst();

    assertFalse(failedContainer.isEmpty());
    assertThat(failedContainer.get().getLogs()).isEqualTo("foo");
    assertThat(failedContainer.get().getStatus()).isEqualTo("Error");
    assertThat(failedContainer.get().getExitCode()).isEqualTo("1");

    assertThat(jobStatus.getFailureDetails())
        .isEqualTo(
            "Pod: 'hello' had errors.\n"
                + " Container: 'init-myservice' exited with code: 1.\n"
                + " Status: Error.\n"
                + " Logs: foo");
  }

  @Test
  void testJobFailureDetailsForJobWithSinglePodWithFailedAppContainers() {
    // setup
    KubernetesJobStatus jobStatus = getKubernetesJobStatus("failed-job.yml");

    // when
    jobStatus.captureFailureDetails();

    // then
    assertThat(jobStatus.getMessage()).isEqualTo("Job has reached the specified backoff limit");
    assertThat(jobStatus.getReason()).isEqualTo("BackoffLimitExceeded");
    assertThat(jobStatus.getPods().size()).isEqualTo(1);
    KubernetesJobStatus.PodStatus podStatus = jobStatus.getPods().get(0);
    assertThat(podStatus.getContainerExecutionDetails().size()).isEqualTo(2);
    Optional<KubernetesJobStatus.ContainerExecutionDetails> failedContainer =
        podStatus.getContainerExecutionDetails().stream()
            .filter(c -> c.getName().equals("some-container-name"))
            .findFirst();

    assertFalse(failedContainer.isEmpty());
    assertThat(failedContainer.get().getLogs())
        .isEqualTo(
            "Failed to download the file: foo.\n"
                + "GET Request failed with status code', 404, 'Expected', <HTTPStatus.OK: 200>)\n");
    assertThat(failedContainer.get().getStatus()).isEqualTo("Error");
    assertThat(failedContainer.get().getExitCode()).isEqualTo("1");

    assertThat(jobStatus.getFailureDetails())
        .isEqualTo(
            "Pod: 'hello' had errors.\n"
                + " Container: 'some-container-name' exited with code: 1.\n"
                + " Status: Error.\n"
                + " Logs: Failed to download the file: foo.\n"
                + "GET Request failed with status code', 404, 'Expected', <HTTPStatus.OK: 200>)\n");
  }

  @Test
  void testJobFailureDetailsForJobFailureOnlyWithNoContainerLogs() {
    // setup
    KubernetesManifest testManifest =
        Yaml.loadAs(getResource("runjob-deadline-exceeded.yml"), KubernetesManifest.class);
    V1Job job = KubernetesCacheDataConverter.getResource(testManifest, V1Job.class);

    KubernetesJobStatus jobStatus = new KubernetesJobStatus(job, "mock-account");

    List<KubernetesManifest> pods = ImmutableList.of(testManifest);
    jobStatus.setPods(
        pods.stream()
            .map(
                p -> {
                  V1Pod pod = KubernetesCacheDataConverter.getResource(p, V1Pod.class);
                  return new KubernetesJobStatus.PodStatus(pod);
                })
            .collect(Collectors.toList()));

    // when
    jobStatus.captureFailureDetails();

    // then
    assertThat(jobStatus.getMessage()).isEqualTo("Job was active longer than specified deadline");
    assertThat(jobStatus.getReason()).isEqualTo("DeadlineExceeded");
    assertNull(jobStatus.getFailureDetails());
  }

  private String getResource(String name) {
    try {
      return Resources.toString(
          KubernetesJobStatusTest.class.getResource(name), StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  private KubernetesJobStatus getKubernetesJobStatus(String manifestPath) {
    KubernetesManifest testManifest =
        Yaml.loadAs(getResource("base.yml"), KubernetesManifest.class);

    KubernetesManifest overlay = Yaml.loadAs(getResource(manifestPath), KubernetesManifest.class);
    testManifest.putAll(overlay);

    V1Job job = KubernetesCacheDataConverter.getResource(testManifest, V1Job.class);
    KubernetesJobStatus kubernetesJobStatus = new KubernetesJobStatus(job, "mock-account");

    List<KubernetesManifest> pods = ImmutableList.of(testManifest);
    kubernetesJobStatus.setPods(
        pods.stream()
            .map(
                p -> {
                  V1Pod pod = KubernetesCacheDataConverter.getResource(p, V1Pod.class);
                  return new KubernetesJobStatus.PodStatus(pod);
                })
            .collect(Collectors.toList()));
    return kubernetesJobStatus;
  }
}
