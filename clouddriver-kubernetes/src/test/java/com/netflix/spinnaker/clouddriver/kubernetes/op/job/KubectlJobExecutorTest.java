/*
 * Copyright 2020 Google, Inc.
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

package com.netflix.spinnaker.clouddriver.kubernetes.op.job;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSetMultimap;
import com.google.common.io.Resources;
import com.netflix.spinnaker.clouddriver.jobs.JobExecutor;
import com.netflix.spinnaker.clouddriver.jobs.JobRequest;
import com.netflix.spinnaker.clouddriver.jobs.JobResult;
import com.netflix.spinnaker.clouddriver.jobs.JobResult.Result;
import com.netflix.spinnaker.clouddriver.kubernetes.description.KubernetesPodMetric;
import com.netflix.spinnaker.clouddriver.kubernetes.description.KubernetesPodMetric.ContainerMetric;
import com.netflix.spinnaker.clouddriver.kubernetes.security.KubernetesV2Credentials;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.platform.runner.JUnitPlatform;
import org.junit.runner.RunWith;

@RunWith(JUnitPlatform.class)
final class KubectlJobExecutorTest {
  private static final ObjectMapper objectMapper = new ObjectMapper();

  private static final String NAMESPACE = "test-namespace";

  @Test
  void topPodEmptyOutput() {
    JobExecutor jobExecutor = mock(JobExecutor.class);
    when(jobExecutor.runJob(any(JobRequest.class)))
        .thenReturn(
            JobResult.<String>builder().result(Result.SUCCESS).output("").error("").build());

    KubectlJobExecutor kubectlJobExecutor =
        new KubectlJobExecutor(jobExecutor, "kubectl", "oauth2l");
    Collection<KubernetesPodMetric> podMetrics =
        kubectlJobExecutor.topPod(mockKubernetesV2Credentials(), "test", "");
    assertThat(podMetrics).isEmpty();
  }

  @Test
  void topPodMultipleContainers() throws Exception {
    JobExecutor jobExecutor = mock(JobExecutor.class);
    when(jobExecutor.runJob(any(JobRequest.class)))
        .thenReturn(
            JobResult.<String>builder()
                .result(Result.SUCCESS)
                .output(
                    Resources.toString(
                        KubectlJobExecutor.class.getResource("top-pod.txt"),
                        StandardCharsets.UTF_8))
                .error("")
                .build());

    KubectlJobExecutor kubectlJobExecutor =
        new KubectlJobExecutor(jobExecutor, "kubectl", "oauth2l");
    Collection<KubernetesPodMetric> podMetrics =
        kubectlJobExecutor.topPod(mockKubernetesV2Credentials(), NAMESPACE, "");
    assertThat(podMetrics).hasSize(2);

    ImmutableSetMultimap<String, ContainerMetric> expectedMetrics =
        ImmutableSetMultimap.<String, ContainerMetric>builder()
            .putAll(
                "spinnaker-io-nginx-v000-42gnq",
                ImmutableList.of(
                    new ContainerMetric(
                        "spinnaker-github-io",
                        ImmutableMap.of("CPU(cores)", "0m", "MEMORY(bytes)", "2Mi")),
                    new ContainerMetric(
                        "istio-proxy",
                        ImmutableMap.of("CPU(cores)", "3m", "MEMORY(bytes)", "28Mi")),
                    new ContainerMetric(
                        "istio-init", ImmutableMap.of("CPU(cores)", "0m", "MEMORY(bytes)", "0Mi"))))
            .putAll(
                "spinnaker-io-nginx-v001-jvkgb",
                ImmutableList.of(
                    new ContainerMetric(
                        "spinnaker-github-io",
                        ImmutableMap.of("CPU(cores)", "0m", "MEMORY(bytes)", "2Mi")),
                    new ContainerMetric(
                        "istio-proxy",
                        ImmutableMap.of("CPU(cores)", "32m", "MEMORY(bytes)", "30Mi")),
                    new ContainerMetric(
                        "istio-init", ImmutableMap.of("CPU(cores)", "0m", "MEMORY(bytes)", "0Mi"))))
            .build();

    for (String pod : expectedMetrics.keys()) {
      Optional<KubernetesPodMetric> podMetric =
          podMetrics.stream()
              .filter(metric -> metric.getPodName().equals(pod))
              .filter(metric -> metric.getNamespace().equals(NAMESPACE))
              .findAny();
      assertThat(podMetric.isPresent()).isTrue();
      assertThat(podMetric.get().getContainerMetrics())
          .containsExactlyInAnyOrderElementsOf(expectedMetrics.get(pod));
    }
  }

  /** Returns a mock KubernetesV2Credentials object */
  private static KubernetesV2Credentials mockKubernetesV2Credentials() {
    KubernetesV2Credentials v2Credentials = mock(KubernetesV2Credentials.class);
    when(v2Credentials.getKubectlExecutable()).thenReturn("");
    return v2Credentials;
  }
}
