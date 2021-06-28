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
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSetMultimap;
import com.google.common.io.Resources;
import com.netflix.spinnaker.clouddriver.jobs.JobExecutionException;
import com.netflix.spinnaker.clouddriver.jobs.JobExecutor;
import com.netflix.spinnaker.clouddriver.jobs.JobRequest;
import com.netflix.spinnaker.clouddriver.jobs.JobResult;
import com.netflix.spinnaker.clouddriver.jobs.JobResult.Result;
import com.netflix.spinnaker.clouddriver.kubernetes.config.KubernetesConfigurationProperties;
import com.netflix.spinnaker.clouddriver.kubernetes.description.KubernetesPodMetric;
import com.netflix.spinnaker.clouddriver.kubernetes.description.KubernetesPodMetric.ContainerMetric;
import com.netflix.spinnaker.clouddriver.kubernetes.security.KubernetesCredentials;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.platform.runner.JUnitPlatform;
import org.junit.runner.RunWith;

@RunWith(JUnitPlatform.class)
final class KubectlJobExecutorTest {
  private static final String NAMESPACE = "test-namespace";

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  void topPodEmptyOutput(boolean retriesEnabled) {
    JobExecutor jobExecutor = mock(JobExecutor.class);
    when(jobExecutor.runJob(any(JobRequest.class)))
        .thenReturn(
            JobResult.<String>builder().result(Result.SUCCESS).output("").error("").build());

    KubernetesConfigurationProperties kubernetesConfigurationProperties =
        new KubernetesConfigurationProperties();
    kubernetesConfigurationProperties.getJobExecutor().getRetries().setEnabled(retriesEnabled);

    KubectlJobExecutor kubectlJobExecutor =
        new KubectlJobExecutor(
            jobExecutor, "kubectl", "oauth2l", kubernetesConfigurationProperties);
    Collection<KubernetesPodMetric> podMetrics =
        kubectlJobExecutor.topPod(mockKubernetesCredentials(), "test", "");
    assertThat(podMetrics).isEmpty();

    // should only be called once as no retries are performed
    verify(jobExecutor).runJob(any(JobRequest.class));
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
        new KubectlJobExecutor(
            jobExecutor, "kubectl", "oauth2l", new KubernetesConfigurationProperties());
    Collection<KubernetesPodMetric> podMetrics =
        kubectlJobExecutor.topPod(mockKubernetesCredentials(), NAMESPACE, "");
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

  @Test
  void kubectlRetryHandlingForConfiguredErrorsThatContinueFailingAfterMaxRetryAttempts() {
    JobExecutor jobExecutor = mock(JobExecutor.class);
    when(jobExecutor.runJob(any(JobRequest.class)))
        .thenReturn(
            JobResult.<String>builder()
                .result(Result.FAILURE)
                .output("")
                .error("Unable to connect to the server: net/http: TLS handshake timeout")
                .build());

    KubernetesConfigurationProperties kubernetesConfigurationProperties =
        new KubernetesConfigurationProperties();
    kubernetesConfigurationProperties.getJobExecutor().getRetries().setEnabled(true);
    kubernetesConfigurationProperties.getJobExecutor().getRetries().setBackOffInMs(500);

    KubectlJobExecutor kubectlJobExecutor =
        new KubectlJobExecutor(
            jobExecutor, "kubectl", "oauth2l", kubernetesConfigurationProperties);

    KubectlJobExecutor.KubectlException thrown =
        assertThrows(
            KubectlJobExecutor.KubectlException.class,
            () -> kubectlJobExecutor.topPod(mockKubernetesCredentials(), "test", "test-pod"));

    // should be called 3 times as there were max 3 attempts made
    verify(jobExecutor, times(3)).runJob(any(JobRequest.class));

    // at the end of retries, the exception should still be thrown
    assertTrue(
        thrown
            .getMessage()
            .contains("Unable to connect to the server: net/http: TLS handshake timeout"));
  }

  @Test
  void kubectlRetryHandlingForConfiguredErrorsThatSucceedsAfterAFewRetries() throws IOException {
    JobExecutor jobExecutor = mock(JobExecutor.class);
    when(jobExecutor.runJob(any(JobRequest.class)))
        .thenReturn(
            JobResult.<String>builder()
                .result(Result.FAILURE)
                .output("")
                .error("Unable to connect to the server: net/http: TLS handshake timeout")
                .build())
        .thenReturn(
            JobResult.<String>builder()
                .result(Result.SUCCESS)
                .output(
                    Resources.toString(
                        KubectlJobExecutor.class.getResource("top-pod.txt"),
                        StandardCharsets.UTF_8))
                .error("")
                .build());

    KubernetesConfigurationProperties kubernetesConfigurationProperties =
        new KubernetesConfigurationProperties();
    kubernetesConfigurationProperties.getJobExecutor().getRetries().setEnabled(true);
    kubernetesConfigurationProperties.getJobExecutor().getRetries().setBackOffInMs(500);

    KubectlJobExecutor kubectlJobExecutor =
        new KubectlJobExecutor(
            jobExecutor, "kubectl", "oauth2l", kubernetesConfigurationProperties);

    Collection<KubernetesPodMetric> podMetrics =
        kubectlJobExecutor.topPod(mockKubernetesCredentials(), NAMESPACE, "test-pod");
    assertThat(podMetrics).hasSize(2);

    // should only be called twice as it failed on the first call but succeeded in the second one
    verify(jobExecutor, times(2)).runJob(any(JobRequest.class));

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

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  void kubectlJobExecutorHandlingForErrorsThatAreNotConfiguredToBeRetryable(
      boolean retriesEnabled) {
    JobExecutor jobExecutor = mock(JobExecutor.class);
    when(jobExecutor.runJob(any(JobRequest.class)))
        .thenReturn(
            JobResult.<String>builder()
                .result(Result.FAILURE)
                .output("")
                .error("un-retryable error")
                .build());

    KubernetesConfigurationProperties kubernetesConfigurationProperties =
        new KubernetesConfigurationProperties();
    kubernetesConfigurationProperties.getJobExecutor().getRetries().setEnabled(retriesEnabled);

    KubectlJobExecutor kubectlJobExecutor =
        new KubectlJobExecutor(
            jobExecutor, "kubectl", "oauth2l", kubernetesConfigurationProperties);

    KubectlJobExecutor.KubectlException thrown =
        assertThrows(
            KubectlJobExecutor.KubectlException.class,
            () -> kubectlJobExecutor.topPod(mockKubernetesCredentials(), "test", ""));

    assertTrue(thrown.getMessage().contains("un-retryable error"));
    // should only be called once as no retries are performed for this error
    verify(jobExecutor).runJob(any(JobRequest.class));
  }

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  void kubectlJobExecutorRaisesException(boolean retriesEnabled) {
    JobExecutor jobExecutor = mock(JobExecutor.class);
    when(jobExecutor.runJob(any(JobRequest.class)))
        .thenThrow(new JobExecutionException("unknown exception", new IOException()));

    KubernetesConfigurationProperties kubernetesConfigurationProperties =
        new KubernetesConfigurationProperties();

    if (retriesEnabled) {
      kubernetesConfigurationProperties.getJobExecutor().getRetries().setEnabled(true);
      kubernetesConfigurationProperties.getJobExecutor().getRetries().setBackOffInMs(500);
    }

    KubectlJobExecutor kubectlJobExecutor =
        new KubectlJobExecutor(
            jobExecutor, "kubectl", "oauth2l", kubernetesConfigurationProperties);

    JobExecutionException thrown =
        assertThrows(
            JobExecutionException.class,
            () -> kubectlJobExecutor.topPod(mockKubernetesCredentials(), "test", "test-pod"));

    if (retriesEnabled) {
      // should be called 3 times as there were max 3 attempts made
      verify(jobExecutor, times(3)).runJob(any(JobRequest.class));
    } else {
      verify(jobExecutor).runJob(any(JobRequest.class));
    }

    // at the end, with or without retries, the exception should still be thrown
    assertTrue(thrown.getMessage().contains("unknown exception"));
  }

  /** Returns a mock KubernetesCredentials object */
  private static KubernetesCredentials mockKubernetesCredentials() {
    KubernetesCredentials credentials = mock(KubernetesCredentials.class);
    when(credentials.getAccountName()).thenReturn("mock-account");
    when(credentials.getKubectlExecutable()).thenReturn("");
    return credentials;
  }
}
