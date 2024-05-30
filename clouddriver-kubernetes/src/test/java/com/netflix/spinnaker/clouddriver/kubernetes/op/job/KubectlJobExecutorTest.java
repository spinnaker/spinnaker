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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSetMultimap;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.google.gson.Gson;
import com.netflix.spinnaker.clouddriver.data.task.InMemoryTaskRepository;
import com.netflix.spinnaker.clouddriver.jobs.JobExecutionException;
import com.netflix.spinnaker.clouddriver.jobs.JobExecutor;
import com.netflix.spinnaker.clouddriver.jobs.JobRequest;
import com.netflix.spinnaker.clouddriver.jobs.JobResult;
import com.netflix.spinnaker.clouddriver.jobs.JobResult.Result;
import com.netflix.spinnaker.clouddriver.jobs.local.JobExecutorLocal;
import com.netflix.spinnaker.clouddriver.kubernetes.config.KubernetesConfigurationProperties;
import com.netflix.spinnaker.clouddriver.kubernetes.description.KubernetesPodMetric;
import com.netflix.spinnaker.clouddriver.kubernetes.description.KubernetesPodMetric.ContainerMetric;
import com.netflix.spinnaker.clouddriver.kubernetes.description.manifest.KubernetesManifest;
import com.netflix.spinnaker.clouddriver.kubernetes.op.handler.ManifestFetcher;
import com.netflix.spinnaker.clouddriver.kubernetes.security.KubernetesCredentials;
import com.netflix.spinnaker.clouddriver.kubernetes.security.KubernetesSelector;
import com.netflix.spinnaker.clouddriver.kubernetes.security.KubernetesSelectorList;
import com.netflix.spinnaker.kork.test.log.MemoryAppender;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryRegistry;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.Collectors;
import org.apache.commons.exec.CommandLine;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.LoggerFactory;

final class KubectlJobExecutorTest {
  private static final String NAMESPACE = "test-namespace";
  JobExecutor jobExecutor;
  KubernetesConfigurationProperties kubernetesConfigurationProperties;

  @BeforeEach
  public void setup() {
    jobExecutor = mock(JobExecutor.class);
    kubernetesConfigurationProperties = new KubernetesConfigurationProperties();
    kubernetesConfigurationProperties.getJobExecutor().getRetries().setBackOffInMs(500);
  }

  @ParameterizedTest(name = "{index} ==> retries enabled = {0}")
  @ValueSource(booleans = {true, false})
  void topPodEmptyOutput(boolean retriesEnabled) {
    when(jobExecutor.runJob(any(JobRequest.class)))
        .thenReturn(
            JobResult.<String>builder().result(Result.SUCCESS).output("").error("").build());

    KubernetesConfigurationProperties kubernetesConfigurationProperties =
        new KubernetesConfigurationProperties();
    kubernetesConfigurationProperties.getJobExecutor().getRetries().setEnabled(retriesEnabled);

    KubectlJobExecutor kubectlJobExecutor =
        new KubectlJobExecutor(
            jobExecutor, kubernetesConfigurationProperties, new SimpleMeterRegistry());
    Collection<KubernetesPodMetric> podMetrics =
        kubectlJobExecutor.topPod(mockKubernetesCredentials(), "test", "");
    assertThat(podMetrics).isEmpty();

    // should only be called once as no retries are performed
    verify(jobExecutor).runJob(any(JobRequest.class));

    if (retriesEnabled) {
      // verify retry registry
      assertTrue(kubectlJobExecutor.getRetryRegistry().isPresent());
      RetryRegistry retryRegistry = kubectlJobExecutor.getRetryRegistry().get();
      assertThat(retryRegistry.getAllRetries().size()).isEqualTo(1);
      assertThat(retryRegistry.getAllRetries().get(0).getName()).isEqualTo("mock-account");

      // verify retry metrics
      Retry.Metrics retryMetrics = retryRegistry.getAllRetries().get(0).getMetrics();
      assertThat(retryMetrics.getNumberOfSuccessfulCallsWithRetryAttempt()).isEqualTo(0);
      // in this test, the action succeeded without retries. So number of unique calls == 1.
      assertThat(retryMetrics.getNumberOfSuccessfulCallsWithoutRetryAttempt()).isEqualTo(1);
      assertThat(retryMetrics.getNumberOfFailedCallsWithRetryAttempt()).isEqualTo(0);
      assertThat(retryMetrics.getNumberOfFailedCallsWithoutRetryAttempt()).isEqualTo(0);
    }
  }

  @Test
  void topPodMultipleContainers() {
    when(jobExecutor.runJob(any(JobRequest.class)))
        .thenReturn(
            JobResult.<String>builder()
                .result(Result.SUCCESS)
                .output(ManifestFetcher.getResource(KubectlJobExecutorTest.class, "top-pod.txt"))
                .error("")
                .build());

    KubectlJobExecutor kubectlJobExecutor =
        new KubectlJobExecutor(
            jobExecutor, new KubernetesConfigurationProperties(), new SimpleMeterRegistry());
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

  @DisplayName("test to verify how kubectl errors are handled when retries are disabled")
  @Test
  void kubectlJobExecutorErrorHandlingWhenRetriesAreDisabled() {
    // when
    when(jobExecutor.runJob(any(JobRequest.class)))
        .thenReturn(
            JobResult.<String>builder()
                .result(Result.FAILURE)
                .output("")
                .error("some error")
                .build());

    KubectlJobExecutor kubectlJobExecutor =
        new KubectlJobExecutor(
            jobExecutor, kubernetesConfigurationProperties, new SimpleMeterRegistry());

    // then
    KubectlJobExecutor.KubectlException thrown =
        assertThrows(
            KubectlJobExecutor.KubectlException.class,
            () -> kubectlJobExecutor.topPod(mockKubernetesCredentials(), "test", ""));

    assertTrue(thrown.getMessage().contains("some error"));
    // should only be called once as no retries are performed for this error
    verify(jobExecutor).runJob(any(JobRequest.class));
  }

  @DisplayName(
      "parameterized test to verify retry behavior for configured retryable errors that fail even after all "
          + "attempts are exhausted")
  @ParameterizedTest(
      name = "{index} ==> number of simultaneous executions of the action under test = {0}")
  @ValueSource(ints = {1, 10})
  void kubectlRetryHandlingForConfiguredErrorsThatContinueFailingAfterMaxRetryAttempts(
      int numberOfThreads) {
    // setup
    kubernetesConfigurationProperties.getJobExecutor().getRetries().setEnabled(true);

    // to test log messages
    MemoryAppender memoryAppender = new MemoryAppender(KubectlJobExecutor.class);

    final ExecutorService executor =
        Executors.newFixedThreadPool(
            numberOfThreads,
            new ThreadFactoryBuilder()
                .setNameFormat(KubectlJobExecutorTest.class.getSimpleName() + "-%d")
                .build());

    final ArrayList<Future<ImmutableList<KubernetesPodMetric>>> futures =
        new ArrayList<>(numberOfThreads);

    // when
    when(jobExecutor.runJob(any(JobRequest.class)))
        .thenReturn(
            JobResult.<String>builder()
                .result(Result.FAILURE)
                .output("")
                .error("Unable to connect to the server: net/http: TLS handshake timeout")
                .build());

    KubectlJobExecutor kubectlJobExecutor =
        new KubectlJobExecutor(
            jobExecutor, kubernetesConfigurationProperties, new SimpleMeterRegistry());

    for (int i = 1; i <= numberOfThreads; i++) {
      futures.add(
          executor.submit(
              () -> kubectlJobExecutor.topPod(mockKubernetesCredentials(), NAMESPACE, "test-pod")));
    }

    // then
    for (Future<ImmutableList<KubernetesPodMetric>> future : futures) {
      try {
        future.get();
      } catch (final ExecutionException e) {
        assertTrue(e.getCause() instanceof KubectlJobExecutor.KubectlException);
        assertTrue(
            e.getMessage()
                .contains("Unable to connect to the server: net/http: TLS handshake timeout"));
      } catch (final InterruptedException ignored) {
      }
    }

    executor.shutdown();

    // verify that the kubectl job executor made max configured attempts per thread to execute the
    // action
    verify(
            jobExecutor,
            times(
                kubernetesConfigurationProperties.getJobExecutor().getRetries().getMaxAttempts()
                    * numberOfThreads))
        .runJob(any(JobRequest.class));

    // verify retry registry
    assertTrue(kubectlJobExecutor.getRetryRegistry().isPresent());
    RetryRegistry retryRegistry = kubectlJobExecutor.getRetryRegistry().get();
    assertThat(retryRegistry.getAllRetries().size()).isEqualTo(1);
    assertThat(retryRegistry.getAllRetries().get(0).getName()).isEqualTo("mock-account");

    // verify retry metrics
    Retry.Metrics retryMetrics = retryRegistry.getAllRetries().get(0).getMetrics();
    assertThat(retryMetrics.getNumberOfSuccessfulCallsWithRetryAttempt()).isEqualTo(0);
    assertThat(retryMetrics.getNumberOfSuccessfulCallsWithoutRetryAttempt()).isEqualTo(0);
    // in this test, all threads failed. So number of unique failed calls == 1 per thread.
    assertThat(retryMetrics.getNumberOfFailedCallsWithRetryAttempt()).isEqualTo(numberOfThreads);
    assertThat(retryMetrics.getNumberOfFailedCallsWithoutRetryAttempt()).isEqualTo(0);

    // verify that no duplicate messages are shown in the logs
    List<String> numberOfFailedRetryAttemptLogMessages =
        memoryAppender.search(
            "Kubectl command for mock-account failed after "
                + kubernetesConfigurationProperties.getJobExecutor().getRetries().getMaxAttempts()
                + " attempts. Exception: com.netflix.spinnaker.clouddriver.kubernetes.op."
                + "job.KubectlJobExecutor$KubectlException: command: 'kubectl "
                + "--request-timeout=0 --namespace=test-namespace top po test-pod "
                + "--containers' in account: mock-account failed. Error: Unable to "
                + "connect to the server: net/http: TLS handshake timeout",
            Level.ERROR);

    // we should only see 1 failed retry attempt message per thread
    assertThat(numberOfFailedRetryAttemptLogMessages.size()).isEqualTo(numberOfThreads);
  }

  @DisplayName(
      "parameterized test to verify retry behavior for errors that are not configured to be retryable")
  @ParameterizedTest(
      name = "{index} ==> number of simultaneous executions of the action under test = {0}")
  @ValueSource(ints = {1, 10})
  void kubectlMultiThreadedRetryHandlingForErrorsThatAreNotConfiguredToBeRetryable(
      int numberOfThreads) {
    // setup
    kubernetesConfigurationProperties.getJobExecutor().getRetries().setEnabled(true);

    // to test log messages
    Logger logger = (Logger) LoggerFactory.getLogger(KubectlJobExecutor.class);
    ListAppender<ILoggingEvent> listAppender = new ListAppender<>();
    listAppender.setContext((LoggerContext) LoggerFactory.getILoggerFactory());
    logger.addAppender(listAppender);
    listAppender.start();

    final ExecutorService executor =
        Executors.newFixedThreadPool(
            numberOfThreads,
            new ThreadFactoryBuilder()
                .setNameFormat(KubectlJobExecutorTest.class.getSimpleName() + "-%d")
                .build());

    final ArrayList<Future<ImmutableList<KubernetesPodMetric>>> futures =
        new ArrayList<>(numberOfThreads);

    // when
    when(jobExecutor.runJob(any(JobRequest.class)))
        .thenReturn(
            JobResult.<String>builder()
                .result(Result.FAILURE)
                .output("")
                .error("un-retryable error")
                .build());

    KubectlJobExecutor kubectlJobExecutor =
        new KubectlJobExecutor(
            jobExecutor, kubernetesConfigurationProperties, new SimpleMeterRegistry());

    for (int i = 1; i <= numberOfThreads; i++) {
      futures.add(
          executor.submit(
              () -> kubectlJobExecutor.topPod(mockKubernetesCredentials(), NAMESPACE, "test-pod")));
    }

    // then
    for (Future<ImmutableList<KubernetesPodMetric>> future : futures) {
      try {
        future.get();
      } catch (final ExecutionException e) {
        assertTrue(e.getCause() instanceof KubectlJobExecutor.KubectlException);
        assertTrue(e.getMessage().contains("un-retryable error"));
      } catch (final InterruptedException ignored) {
      }
    }

    executor.shutdown();

    // verify that the kubectl job executor tried once to execute the action once per thread
    verify(jobExecutor, times(numberOfThreads)).runJob(any(JobRequest.class));

    // verify retry registry
    assertTrue(kubectlJobExecutor.getRetryRegistry().isPresent());
    RetryRegistry retryRegistry = kubectlJobExecutor.getRetryRegistry().get();
    assertThat(retryRegistry.getAllRetries().size()).isEqualTo(1);
    assertThat(retryRegistry.getAllRetries().get(0).getName()).isEqualTo("mock-account");

    // verify retry metrics
    Retry.Metrics retryMetrics = retryRegistry.getAllRetries().get(0).getMetrics();
    assertThat(retryMetrics.getNumberOfSuccessfulCallsWithRetryAttempt()).isEqualTo(0);
    assertThat(retryMetrics.getNumberOfSuccessfulCallsWithoutRetryAttempt()).isEqualTo(0);
    assertThat(retryMetrics.getNumberOfFailedCallsWithRetryAttempt()).isEqualTo(0);
    // in this test, all threads failed without retrying. So number of unique failed calls == 1 per
    // thread.
    assertThat(retryMetrics.getNumberOfFailedCallsWithoutRetryAttempt()).isEqualTo(numberOfThreads);

    // verify that no duplicate messages are shown in the logs
    List<ILoggingEvent> logsList = listAppender.list;
    List<ILoggingEvent> numberOfFailedRetryAttemptLogMessages =
        logsList.stream()
            .filter(
                iLoggingEvent ->
                    iLoggingEvent
                        .getFormattedMessage()
                        .contains(
                            "Not retrying command: 'kubectl --request-timeout=0 --namespace=test-namespace"
                                + " top po test-pod --containers' in account: mock-account as retries are not"
                                + " enabled for error: un-retryable error"))
            .collect(Collectors.toList());

    // we should only see 1 failed retry attempt message per thread
    assertThat(numberOfFailedRetryAttemptLogMessages.size()).isEqualTo(numberOfThreads);
  }

  @Test
  void kubectlRetryHandlingForConfiguredErrorsThatSucceedAfterAFewRetries() {
    // setup
    kubernetesConfigurationProperties.getJobExecutor().getRetries().setEnabled(true);

    // to test log messages
    Logger logger = (Logger) LoggerFactory.getLogger(KubectlJobExecutor.class);
    ListAppender<ILoggingEvent> listAppender = new ListAppender<>();
    listAppender.setContext((LoggerContext) LoggerFactory.getILoggerFactory());
    logger.addAppender(listAppender);
    listAppender.start();

    // when
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
                .output(ManifestFetcher.getResource(KubectlJobExecutorTest.class, "top-pod.txt"))
                .error("")
                .build());

    KubectlJobExecutor kubectlJobExecutor =
        new KubectlJobExecutor(
            jobExecutor, kubernetesConfigurationProperties, new SimpleMeterRegistry());

    Collection<KubernetesPodMetric> podMetrics =
        kubectlJobExecutor.topPod(mockKubernetesCredentials(), NAMESPACE, "test-pod");

    // then

    // job executor should be called twice - as it failed on the first call but succeeded
    // in the second one
    verify(jobExecutor, times(2)).runJob(any(JobRequest.class));

    // verify retry registry
    assertTrue(kubectlJobExecutor.getRetryRegistry().isPresent());
    RetryRegistry retryRegistry = kubectlJobExecutor.getRetryRegistry().get();
    assertThat(retryRegistry.getAllRetries().size()).isEqualTo(1);
    assertThat(retryRegistry.getAllRetries().get(0).getName()).isEqualTo("mock-account");

    // verify retry metrics
    Retry.Metrics retryMetrics = retryRegistry.getAllRetries().get(0).getMetrics();
    // in this test, the action succeeded eventually. So number of unique calls == 1.
    assertThat(retryMetrics.getNumberOfSuccessfulCallsWithRetryAttempt()).isEqualTo(1);
    assertThat(retryMetrics.getNumberOfSuccessfulCallsWithoutRetryAttempt()).isEqualTo(0);
    assertThat(retryMetrics.getNumberOfFailedCallsWithRetryAttempt()).isEqualTo(0);
    assertThat(retryMetrics.getNumberOfFailedCallsWithoutRetryAttempt()).isEqualTo(0);

    // verify that no duplicate messages are shown in the logs
    List<ILoggingEvent> logsList = listAppender.list;
    List<ILoggingEvent> numberOfSucceededRetryAttemptsLogMessages =
        logsList.stream()
            .filter(
                iLoggingEvent ->
                    iLoggingEvent
                        .getFormattedMessage()
                        .contains(
                            "Kubectl command for mock-account is now successful in attempt #2. Last "
                                + "attempt had failed with exception: com.netflix.spinnaker.clouddriver"
                                + ".kubernetes.op.job.KubectlJobExecutor$KubectlException: command: "
                                + "'kubectl --request-timeout=0 --namespace=test-namespace top po test-pod"
                                + " --containers' in account: mock-account failed. Error: Unable to connect to"
                                + " the server: net/http: TLS handshake timeout"))
            .collect(Collectors.toList());

    // we should only see 1 succeeded retry attempt message
    assertThat(numberOfSucceededRetryAttemptsLogMessages.size()).isEqualTo(1);

    // verify output of the command
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

  @ParameterizedTest(name = "{index} ==> retries enabled = {0}")
  @ValueSource(booleans = {true, false})
  void kubectlJobExecutorRaisesException(boolean retriesEnabled) {
    when(jobExecutor.runJob(any(JobRequest.class)))
        .thenThrow(new JobExecutionException("unknown exception", new IOException()));

    if (retriesEnabled) {
      kubernetesConfigurationProperties.getJobExecutor().getRetries().setEnabled(true);
    }

    KubectlJobExecutor kubectlJobExecutor =
        new KubectlJobExecutor(
            jobExecutor, kubernetesConfigurationProperties, new SimpleMeterRegistry());

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

  @DisplayName(
      "test to verify that kubectl commands that read data from stdin can succeed in subsequent retry attempts")
  @Test
  void kubectlRetryHandlingForKubectlCallsThatUseStdinWhichSucceedAfterAFewRetries() {
    // setup
    kubernetesConfigurationProperties.getJobExecutor().getRetries().setEnabled(true);

    // fetch a test manifest
    KubernetesManifest inputManifest =
        ManifestFetcher.getManifest(KubectlJobExecutorTest.class, "job.yml").get(0);

    KubectlJobExecutor kubectlJobExecutor =
        new TestScriptJobExecutor(
            new JobExecutorLocal(/* timeoutMinutes */ 1),
            kubernetesConfigurationProperties,
            new SimpleMeterRegistry(),
            TestScriptJobExecutor.RetryBehavior.SUCCESS_AFTER_INITIAL_FAILURE);

    // We are using a real job executor. Therefore, we can simulate the call `kubectl apply -f -`
    // by substituting kubectl with a test script that accepts stdin
    KubernetesManifest returnedManifest =
        kubectlJobExecutor.deploy(
            mockKubernetesCredentials(
                "src/test/resources/com/netflix/spinnaker/clouddriver/kubernetes/op/job/mock-kubectl-stdin-command.sh"),
            inputManifest,
            new InMemoryTaskRepository().create("starting", "starting"),
            "starting",
            new KubernetesSelectorList());

    // even after retries occur, the inputStream should not empty. This is verified by
    // checking the stdout generated from the script
    assertThat(returnedManifest.getFullResourceName())
        .isEqualTo(inputManifest.getFullResourceName());
  }

  @DisplayName(
      "test to verify that kubectl commands that read data from stdin fail after all retries. In each retry attempt,"
          + " the input stream data should still be made available to the call")
  @Test
  void kubectlRetryHandlingForKubectlCallsThatUseStdinWhichContinueFailingAfterAllRetries() {
    // setup
    kubernetesConfigurationProperties.getJobExecutor().getRetries().setEnabled(true);

    // fetch a test manifest
    KubernetesManifest inputManifest =
        ManifestFetcher.getManifest(KubectlJobExecutorTest.class, "job.yml").get(0);

    KubectlJobExecutor kubectlJobExecutor =
        new TestScriptJobExecutor(
            new JobExecutorLocal(/* timeoutMinutes */ 1),
            kubernetesConfigurationProperties,
            new SimpleMeterRegistry(),
            TestScriptJobExecutor.RetryBehavior.FAILED);

    // We are using a real job executor. Therefore, we can simulate the call `kubectl apply -f -`
    // by substituting kubectl with a test script that accepts stdin
    KubectlJobExecutor.KubectlException thrown =
        assertThrows(
            KubectlJobExecutor.KubectlException.class,
            () ->
                kubectlJobExecutor.deploy(
                    mockKubernetesCredentials(
                        "src/test/resources/com/netflix/spinnaker/clouddriver/kubernetes/op/job/mock-kubectl-stdin-command.sh"),
                    inputManifest,
                    new InMemoryTaskRepository().create("starting", "starting"),
                    "starting",
                    new KubernetesSelectorList()));

    assertThat(thrown.getMessage()).contains("Deploy failed for manifest: job my-job");
    // verify that the final error contained stdin data
    assertThat(thrown.getMessage()).contains(new Gson().toJson(inputManifest));
  }

  @Test
  void testDeployNoObjectsPassedToApplyNoLabelSelectors() {
    // given
    when(jobExecutor.runJob(any(JobRequest.class)))
        .thenReturn(
            JobResult.<String>builder()
                .result(Result.FAILURE)
                .output("")
                .error("error: no objects passed to apply")
                .build());

    KubectlJobExecutor kubectlJobExecutor =
        new KubectlJobExecutor(
            jobExecutor, kubernetesConfigurationProperties, new SimpleMeterRegistry());

    KubernetesManifest manifest = new KubernetesManifest();
    manifest.putAll(
        Map.of(
            "kind",
            "Job", // arbitrary kind
            "metadata",
            Map.of("name", "my-name")));

    KubernetesSelectorList labelSelectors = new KubernetesSelectorList();
    assertThat(labelSelectors.isEmpty()).isTrue();

    // With no label selectors, expect deploy to throw a KubectlException because kubectl has failed
    // (i.e. returned a non-zero exit code).
    assertThatThrownBy(
            () ->
                kubectlJobExecutor.deploy(
                    mockKubernetesCredentials(),
                    manifest,
                    new InMemoryTaskRepository().create("task", "task"),
                    "operation",
                    labelSelectors))
        .isInstanceOf(KubectlJobExecutor.KubectlException.class)
        .hasMessageContaining("Deploy failed for manifest:");
  }

  @Test
  void testDeployNoObjectsPassedToApplyWithLabelSelectors() {
    // given
    when(jobExecutor.runJob(any(JobRequest.class)))
        .thenReturn(
            JobResult.<String>builder()
                .result(Result.FAILURE)
                .output("")
                .error("error: no objects passed to apply")
                .build());

    KubectlJobExecutor kubectlJobExecutor =
        new KubectlJobExecutor(
            jobExecutor, kubernetesConfigurationProperties, new SimpleMeterRegistry());

    KubernetesManifest manifest = new KubernetesManifest();
    manifest.putAll(
        Map.of(
            "kind",
            "Job", // arbitrary kind
            "metadata",
            Map.of("name", "my-name")));

    KubernetesSelectorList labelSelectors = new KubernetesSelectorList();
    KubernetesSelector selector =
        new KubernetesSelector(KubernetesSelector.Kind.EQUALS, "some-key", List.of("some-value"));
    labelSelectors.addSelector(selector);
    assertThat(labelSelectors.isNotEmpty()).isTrue();

    // With a label selectors, expect deploy to return null with the expectation
    // that higher level code (e.g. KubernetesDeployManifestOperation) raises an
    // exception if none of the deploy calls it makes result in kubectl actually
    // deploying a manifest.
    KubernetesManifest returnedManifest =
        kubectlJobExecutor.deploy(
            mockKubernetesCredentials(),
            manifest,
            new InMemoryTaskRepository().create("task", "task"),
            "operation",
            labelSelectors);
    assertThat(returnedManifest).isNull();
  }

  @Test
  void testCreateNoObjectsPassedToCreateWithLabelSelectors() {
    // given
    when(jobExecutor.runJob(any(JobRequest.class)))
        .thenReturn(
            JobResult.<String>builder()
                .result(Result.FAILURE)
                .output("")
                .error("error: no objects passed to create")
                .build());

    KubectlJobExecutor kubectlJobExecutor =
        new KubectlJobExecutor(
            jobExecutor, kubernetesConfigurationProperties, new SimpleMeterRegistry());

    KubernetesManifest manifest = new KubernetesManifest();
    manifest.putAll(
        Map.of(
            "kind",
            "Job", // arbitrary kind
            "metadata",
            Map.of("name", "my-name")));

    KubernetesSelectorList labelSelectors = new KubernetesSelectorList();
    KubernetesSelector selector =
        new KubernetesSelector(KubernetesSelector.Kind.EQUALS, "some-key", List.of("some-value"));
    labelSelectors.addSelector(selector);
    assertThat(labelSelectors.isNotEmpty()).isTrue();

    // With a label selectors, expect create to return null with the
    // expectation that higher level code
    // (e.g. KubernetesDeployManifestOperation) raises an exception if none of
    // the deploy calls it makes result in kubectl actually deploying a
    // manifest.
    KubernetesManifest returnedManifest =
        kubectlJobExecutor.create(
            mockKubernetesCredentials(),
            manifest,
            new InMemoryTaskRepository().create("task", "task"),
            "operation",
            labelSelectors);
    assertThat(returnedManifest).isNull();
  }

  /** Returns a mock KubernetesCredentials object */
  private static KubernetesCredentials mockKubernetesCredentials() {
    return mockKubernetesCredentials("");
  }

  /**
   * Returns a mock KubernetesCredentials object which has a custom path set for the kubectl
   * executable
   */
  private static KubernetesCredentials mockKubernetesCredentials(String pathToExecutable) {
    KubernetesCredentials credentials = mock(KubernetesCredentials.class);
    when(credentials.getAccountName()).thenReturn("mock-account");
    when(credentials.getKubectlExecutable()).thenReturn(pathToExecutable);
    return credentials;
  }

  /**
   * This is a helper class that is meant to execute a custom command instead of kubectl commands.
   * Only meant to be used in tests where mocking certain kubectl calls prove to be tricky. This is
   * currently used in tests that verify retry behavior for such calls.
   */
  private static class TestScriptJobExecutor extends KubectlJobExecutor {
    /**
     * depending on the custom script provided, to simulate retry attempts, we need to let the
     * script know when to emit an error message vs when to emit a success message. These enums help
     * govern that
     */
    private enum RetryBehavior {
      SUCCESS_AFTER_INITIAL_FAILURE,
      FAILED
    }

    private final RetryBehavior retryBehavior;

    // this keeps a track of how many times has the createJobRequest() been invoked.

    private int createJobRequestInvokedCounter;

    TestScriptJobExecutor(
        JobExecutor jobExecutor,
        KubernetesConfigurationProperties kubernetesConfigurationProperties,
        MeterRegistry meterRegistry,
        RetryBehavior retryBehavior) {

      super(jobExecutor, kubernetesConfigurationProperties, meterRegistry);
      this.retryBehavior = retryBehavior;
      this.createJobRequestInvokedCounter = 1;
    }

    @Override
    JobRequest createJobRequest(List<String> command, Optional<KubernetesManifest> manifest) {
      // command[0] contains the path to the custom script. This path is read from the credentials
      // object used for running the command.
      // Note: CommandLine requires a File object containing the path to the script to be able to
      // execute these scripts. This is different from running executables like kubectl.
      CommandLine commandLine = new CommandLine(new File(command.get(0)));

      // this adds a special argument to the test script. The script can use this to decide at
      // runtime if it needs to exit successfully or with a failure.
      // This will be the first argument to the script
      if (createJobRequestInvokedCounter > 1
          && retryBehavior == RetryBehavior.SUCCESS_AFTER_INITIAL_FAILURE) {
        commandLine.addArgument("success");
      }

      createJobRequestInvokedCounter++;

      // update the command line to include all the other arguments to the script
      for (int i = 1; i < command.size(); i++) {
        commandLine.addArgument(command.get(i));
      }

      // depending on the presence of the manifest, an appropriate job request is created
      if (manifest.isPresent()) {
        String manifestAsJson = new Gson().toJson(manifest.get());
        return new JobRequest(
            commandLine, new ByteArrayInputStream(manifestAsJson.getBytes(StandardCharsets.UTF_8)));
      }

      return new JobRequest(commandLine, new ByteArrayInputStream(new byte[0]));
    }
  }
}
