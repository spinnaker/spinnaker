/*
 * Copyright 2020 Netflix, Inc.
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
 */

package com.netflix.spinnaker.orca.clouddriver.tasks.job;

import static com.netflix.spinnaker.orca.TestUtils.getResource;
import static com.netflix.spinnaker.orca.TestUtils.getResourceAsStream;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.netflix.spinnaker.kork.core.RetrySupport;
import com.netflix.spinnaker.kork.exceptions.ConfigurationException;
import com.netflix.spinnaker.orca.api.pipeline.TaskResult;
import com.netflix.spinnaker.orca.api.pipeline.models.ExecutionStatus;
import com.netflix.spinnaker.orca.api.pipeline.models.ExecutionType;
import com.netflix.spinnaker.orca.api.pipeline.models.StageExecution;
import com.netflix.spinnaker.orca.clouddriver.KatoRestService;
import com.netflix.spinnaker.orca.clouddriver.config.TaskConfigurationProperties;
import com.netflix.spinnaker.orca.clouddriver.config.tasks.RetryConfig;
import com.netflix.spinnaker.orca.clouddriver.exception.JobFailedException;
import com.netflix.spinnaker.orca.front50.Front50Service;
import com.netflix.spinnaker.orca.front50.model.Application;
import com.netflix.spinnaker.orca.pipeline.model.PipelineExecutionImpl;
import com.netflix.spinnaker.orca.pipeline.model.StageExecutionImpl;
import com.netflix.spinnaker.orca.pipeline.persistence.ExecutionRepository;
import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.apache.commons.io.IOUtils;
import org.assertj.core.api.AssertionsForClassTypes;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import retrofit.client.Response;
import retrofit.mime.TypedByteArray;

public final class WaitOnJobCompletionTest {
  private ObjectMapper objectMapper;
  private KatoRestService mockKatoRestService;
  private ExecutionRepository mockExecutionRepository;
  private TaskConfigurationProperties configProperties;
  private Front50Service mockFront50Service;
  WaitOnJobCompletion task;

  @BeforeEach
  public void setup() {
    objectMapper = new ObjectMapper();
    RetrySupport retrySupport = new RetrySupport();
    mockKatoRestService = mock(KatoRestService.class);
    JobUtils mockJobUtils = mock(JobUtils.class);
    mockExecutionRepository = mock(ExecutionRepository.class);
    mockFront50Service = mock(Front50Service.class);

    configProperties = new TaskConfigurationProperties();
    RetryConfig retries = new RetryConfig();
    retries.setMaxAttempts(3);
    retries.setBackOffInMs(1);
    configProperties.getWaitOnJobCompletionTask().setFileContentRetry(retries);
    configProperties.getWaitOnJobCompletionTask().setJobStatusRetry(retries);
    configProperties
        .getWaitOnJobCompletionTask()
        .setExcludeKeysFromOutputs(Set.of("completionDetails"));

    task =
        new WaitOnJobCompletion(
            mockKatoRestService,
            objectMapper,
            retrySupport,
            mockJobUtils,
            mockFront50Service,
            configProperties,
            mockExecutionRepository);
  }

  @Test
  void jobTimeoutSpecifiedByRunJobTask() {
    Duration duration = Duration.ofMinutes(10);

    StageExecutionImpl myStage =
        createStageWithContext(ImmutableMap.of("jobRuntimeLimit", duration.toString()));
    assertThat(task.getDynamicTimeout(myStage))
        .isEqualTo((duration.plus(WaitOnJobCompletion.getPROVIDER_PADDING())).toMillis());

    StageExecutionImpl myStageInvalid =
        createStageWithContext(ImmutableMap.of("jobRuntimeLimit", "garbage"));
    assertThat(task.getDynamicTimeout(myStageInvalid)).isEqualTo(task.getTimeout());
  }

  @Test
  void taskSearchJobByApplicationUsingContextApplication() {
    Response mockResponse =
        new Response(
            "test-url",
            200,
            "test-reason",
            Collections.emptyList(),
            new TypedByteArray("application/json", "{ \"jobState\": \"Succeeded\"}".getBytes()));

    when(mockKatoRestService.collectJob(any(), any(), any(), any())).thenReturn(mockResponse);

    StageExecutionImpl myStage =
        createStageWithContext(
            ImmutableMap.of(
                "application",
                "context-app",
                "deploy.jobs",
                ImmutableMap.of("test", ImmutableList.of("job test"))));

    TaskResult result = task.execute(myStage);
    AssertionsForClassTypes.assertThat(result.getStatus()).isEqualTo(ExecutionStatus.SUCCEEDED);
    verify(mockKatoRestService, times(1)).collectJob(eq("context-app"), any(), any(), any());
    verify(mockFront50Service, times(0)).get(any());
  }

  @Test
  void taskSearchJobByApplicationUsingContextMoniker() {
    Response mockResponse =
        new Response(
            "test-url",
            200,
            "test-reason",
            Collections.emptyList(),
            new TypedByteArray("application/json", "{ \"jobState\": \"Succeeded\"}".getBytes()));

    when(mockKatoRestService.collectJob(any(), any(), any(), any())).thenReturn(mockResponse);

    StageExecutionImpl myStage =
        createStageWithContext(
            ImmutableMap.of(
                "moniker", ImmutableMap.of("app", "moniker-app"),
                "application", "context-app",
                "deploy.jobs", ImmutableMap.of("test", ImmutableList.of("job test"))));

    TaskResult result = task.execute(myStage);
    AssertionsForClassTypes.assertThat(result.getStatus()).isEqualTo(ExecutionStatus.SUCCEEDED);
    verify(mockKatoRestService, times(1)).collectJob(eq("moniker-app"), any(), any(), any());
    verify(mockFront50Service, times(0)).get(any());
  }

  @Test
  void taskSearchJobByApplicationUsingParsedName() {
    Response mockResponse =
        new Response(
            "test-url",
            200,
            "test-reason",
            Collections.emptyList(),
            new TypedByteArray("application/json", "{ \"jobState\": \"Succeeded\"}".getBytes()));

    when(mockKatoRestService.collectJob(any(), any(), any(), any())).thenReturn(mockResponse);
    when(mockFront50Service.get(any())).thenReturn(new Application("atest"));

    StageExecutionImpl myStage =
        createStageWithContextWithoutExecutionApplication(
            ImmutableMap.of(
                "deploy.jobs", ImmutableMap.of("test", ImmutableList.of("atest-btest-ctest"))));

    TaskResult result = task.execute(myStage);
    AssertionsForClassTypes.assertThat(result.getStatus()).isEqualTo(ExecutionStatus.SUCCEEDED);
    verify(mockKatoRestService, times(1)).collectJob(eq("atest"), any(), any(), any());
    verify(mockFront50Service, times(1)).get(eq("atest"));
  }

  @Test
  void taskSearchJobByApplicationUsingExecutionApp() {
    Response mockResponse =
        new Response(
            "test-url",
            200,
            "test-reason",
            Collections.emptyList(),
            new TypedByteArray("application/json", "{ \"jobState\": \"Succeeded\"}".getBytes()));

    when(mockKatoRestService.collectJob(any(), any(), any(), any())).thenReturn(mockResponse);

    StageExecutionImpl myStage =
        createStageWithContext(
            ImmutableMap.of(
                "deploy.jobs", ImmutableMap.of("test", ImmutableList.of("atest-btest-ctest"))));

    TaskResult result = task.execute(myStage);
    AssertionsForClassTypes.assertThat(result.getStatus()).isEqualTo(ExecutionStatus.SUCCEEDED);
    verify(mockKatoRestService, times(1)).collectJob(eq("test-app"), any(), any(), any());
    verify(mockFront50Service, times(0)).get(any());
  }

  @DisplayName(
      "parameterized test for checking how property file contents are set in the stage context for a successful k8s runjob")
  @ParameterizedTest(name = "{index} ==> isPropertyFileContentsEmpty = {0}")
  @ValueSource(booleans = {true, false})
  void testPropertyFileContentsHandlingForASuccessfulK8sRunJob(boolean isPropertyFileContentsEmpty)
      throws IOException {
    // setup
    when(mockKatoRestService.collectJob(any(), any(), any(), any()))
        .thenReturn(
            createJobStatusFromResource(
                "clouddriver/tasks/job/kubernetes/successful-runjob-status.json"));

    Map<String, Object> propertyFileContents = new HashMap<>();
    if (!isPropertyFileContentsEmpty) {
      propertyFileContents.put("some key", "some value");
    }
    when(mockKatoRestService.getFileContents(
            eq("test-app"), eq("test-account"), eq("test"), eq("job testrep"), eq("testrep")))
        .thenReturn(propertyFileContents);

    StageExecution myStage =
        createStageFromResource(
            "clouddriver/tasks/job/kubernetes/runjob-stage-context-with-property-file.json");

    // when
    ConfigurationException thrown = null;
    TaskResult result = null;
    if (isPropertyFileContentsEmpty) {
      thrown = assertThrows(ConfigurationException.class, () -> task.execute(myStage));
    } else {
      result = task.execute(myStage);
    }

    // then
    verify(mockKatoRestService, times(1))
        .collectJob(eq("test-app"), eq("test-account"), eq("test"), eq("job testrep"));

    verify(mockKatoRestService, times(1))
        .getFileContents(
            eq("test-app"), eq("test-account"), eq("test"), eq("job testrep"), eq("testrep"));

    verifyNoInteractions(mockFront50Service);
    verifyNoInteractions(mockExecutionRepository);

    if (isPropertyFileContentsEmpty) {
      // the expected exception should be the one that was thrown when retrieving the property file
      // contents
      assertNotNull(thrown);
      assertTrue(
          thrown
              .getMessage()
              .matches(
                  "Expected properties file: testrep in job: job testrep, application: test-app,"
                      + " location: test, account: test-account but it was either missing, empty or"
                      + " contained invalid syntax"));
    } else {
      assertNotNull(result);
      assertThat(result.getContext().containsKey("propertyFileContents")).isTrue();
      assertThat(result.getContext().get("propertyFileContents")).isEqualTo(propertyFileContents);
    }
  }

  @Test
  void testPropertyFileContentsErrorHandlingForASuccessfulK8sRunJob() throws IOException {
    when(mockKatoRestService.collectJob(any(), any(), any(), any()))
        .thenReturn(
            createJobStatusFromResource(
                "clouddriver/tasks/job/kubernetes/successful-runjob-status.json"));

    when(mockKatoRestService.getFileContents(
            eq("test-app"), eq("test-account"), eq("test"), eq("job testrep"), eq("testrep")))
        .thenThrow(new RuntimeException("some exception"));

    // when
    ConfigurationException thrown =
        assertThrows(
            ConfigurationException.class,
            () ->
                task.execute(
                    createStageFromResource(
                        "clouddriver/tasks/job/kubernetes/runjob-stage-context-with-property-file.json")));

    // then
    verify(mockKatoRestService, times(1))
        .collectJob(eq("test-app"), eq("test-account"), eq("test"), eq("job testrep"));

    verify(
            mockKatoRestService,
            times(
                configProperties
                    .getWaitOnJobCompletionTask()
                    .getFileContentRetry()
                    .getMaxAttempts()))
        .getFileContents(
            eq("test-app"), eq("test-account"), eq("test"), eq("job testrep"), eq("testrep"));

    verifyNoInteractions(mockFront50Service);
    verifyNoInteractions(mockExecutionRepository);

    // the expected exception should be the one that was thrown when retrieving the property file
    // contents
    assertTrue(
        thrown
            .getMessage()
            .matches(
                "Expected properties file: testrep in job: job testrep, application: test-app,"
                    + " location: test, account: test-account but it was either missing, empty or contained"
                    + " invalid syntax. Error: java.lang.RuntimeException: some exception"));
  }

  @DisplayName(
      "test to parse properties file for a successful k8s job having 2 pods - first failed, and second succeeded. The"
          + " properties file should be obtained from the getFileContents() call, if that is successful")
  @Test
  void
      testParsePropertiesFileContentsForSuccessfulK8sJobWith2PodsWithSuccessfulGetFileContentsCall()
          throws IOException {
    // setup

    // mocked JobStatus response from clouddriver
    when(mockKatoRestService.collectJob("test-app", "test-account", "test", "job testrep"))
        .thenReturn(
            createJobStatusFromResource(
                "clouddriver/tasks/job/kubernetes/successful-runjob-status-with-multiple-pods.json"));

    // when
    when(mockKatoRestService.getFileContents(
            "test-app", "test-account", "test", "job testrep", "testrep"))
        .thenReturn(Map.of("some-key", "some-value"));

    TaskResult result =
        task.execute(
            createStageFromResource(
                "clouddriver/tasks/job/kubernetes/runjob-stage-context-with-property-file.json"));

    // then
    assertThat(result.getOutputs()).isNotEmpty();
    assertThat(result.getContext()).isNotEmpty();

    assertThat(result.getOutputs().containsKey("some-key"));
    assertThat(result.getOutputs().containsValue("some-value"));
    verify(mockKatoRestService)
        .getFileContents("test-app", "test-account", "test", "job testrep", "testrep");
    // no need to get file contents from a specific pod if the getFileContents call was successful
    verify(mockKatoRestService, never())
        .getFileContentsFromKubernetesPod(
            anyString(), anyString(), anyString(), anyString(), anyString());
  }

  @DisplayName(
      "test to parse properties file for a successful k8s job having 2 pods - first failed, and second succeeded. The"
          + " the properties file should be read directly from the succeeded pod if the getFileContents() call fails")
  @Test
  void testParsePropertiesFileContentsForSuccessfulK8sJobWith2PodsWithFailedGetFileContentsCall()
      throws IOException {
    // setup

    // mocked JobStatus response from clouddriver
    when(mockKatoRestService.collectJob("test-app", "test-account", "test", "job testrep"))
        .thenReturn(
            createJobStatusFromResource(
                "clouddriver/tasks/job/kubernetes/successful-runjob-status-with-multiple-pods.json"));

    // when
    when(mockKatoRestService.getFileContents(
            "test-app", "test-account", "test", "job testrep", "testrep"))
        .thenReturn(Map.of());

    when(mockKatoRestService.getFileContentsFromKubernetesPod(
            "test-app", "test-account", "test", "testrep-rn5qt", "testrep"))
        .thenReturn(Map.of("some-key", "some-value"));

    TaskResult result =
        task.execute(
            createStageFromResource(
                "clouddriver/tasks/job/kubernetes/runjob-stage-context-with-property-file.json"));

    // then
    assertThat(result.getOutputs()).isNotEmpty();
    assertThat(result.getContext()).isNotEmpty();

    assertThat(result.getOutputs().containsKey("some-key"));
    assertThat(result.getOutputs().containsValue("some-value"));
    verify(mockKatoRestService)
        .getFileContents("test-app", "test-account", "test", "job testrep", "testrep");
    verify(mockKatoRestService)
        .getFileContentsFromKubernetesPod(
            "test-app", "test-account", "test", "testrep-rn5qt", "testrep");
  }

  @DisplayName(
      "test to parse properties file for a successful k8s job having 2 pods - first failed, and second succeeded. The"
          + " the properties file should be read from the getFileContents() call first. If that fails, a call to "
          + " get the properties from the getFileContentsFromPod() should be made. If that fails,"
          + " an exception should be thrown")
  @Test
  void testParsePropertiesFileContentsErrorHandlingForSuccessfulK8sJobWith2Pods()
      throws IOException {
    // setup

    // mocked JobStatus response from clouddriver
    when(mockKatoRestService.collectJob("test-app", "test-account", "test", "job testrep"))
        .thenReturn(
            createJobStatusFromResource(
                "clouddriver/tasks/job/kubernetes/successful-runjob-status-with-multiple-pods.json"));

    // when
    when(mockKatoRestService.getFileContents(
            "test-app", "test-account", "test", "job testrep", "testrep"))
        .thenReturn(Map.of());

    when(mockKatoRestService.getFileContentsFromKubernetesPod(
            "test-app", "test-account", "test", "testrep-rn5qt", "testrep"))
        .thenReturn(Map.of());

    // then
    ConfigurationException thrown =
        assertThrows(
            ConfigurationException.class,
            () ->
                task.execute(
                    createStageFromResource(
                        "clouddriver/tasks/job/kubernetes/runjob-stage-context-with-property-file.json")));

    verify(mockKatoRestService)
        .getFileContents("test-app", "test-account", "test", "job testrep", "testrep");
    verify(mockKatoRestService)
        .getFileContentsFromKubernetesPod(
            "test-app", "test-account", "test", "testrep-rn5qt", "testrep");
    assertTrue(
        thrown
            .getMessage()
            .matches(
                "Expected properties file: testrep in job: job testrep, application: test-app,"
                    + " location: test, account: test-account but it was either missing, empty or contained"
                    + " invalid syntax"));
  }

  @DisplayName(
      "parameterized test for checking if an exception is thrown when a k8s run job fails, with or without a propertyFile")
  @ParameterizedTest(name = "{index} ==> includePropertyFile = {0}")
  @ValueSource(booleans = {true, false})
  void testK8sRunJobFailuresErrorHandling(boolean includePropertyFile) throws IOException {
    // setup
    when(mockKatoRestService.collectJob(any(), any(), any(), any()))
        .thenReturn(
            createJobStatusFromResource(
                "clouddriver/tasks/job/kubernetes/failed-runjob-status.json"));

    String stageContextResource =
        "clouddriver/tasks/job/kubernetes/runjob-stage-context-without-property-file.json";
    if (includePropertyFile) {
      stageContextResource =
          "clouddriver/tasks/job/kubernetes/runjob-stage-context-with-property-file.json";
    }

    StageExecution myStage = createStageFromResource(stageContextResource);

    // when
    JobFailedException thrown = assertThrows(JobFailedException.class, () -> task.execute(myStage));

    // then
    verify(mockKatoRestService, times(1))
        .collectJob(eq("test-app"), eq("test-account"), eq("test"), eq("job testrep"));

    if (includePropertyFile) {
      verify(mockKatoRestService, times(1))
          .getFileContents(
              eq("test-app"), eq("test-account"), eq("test"), eq("job testrep"), eq("testrep"));
    } else {
      verify(mockKatoRestService, never())
          .getFileContents(anyString(), anyString(), anyString(), anyString(), anyString());
    }
    verifyNoInteractions(mockFront50Service);

    // check to validate that we update the execution details in the repository before raising an
    // exception
    verify(mockExecutionRepository, times(1)).storeStage(myStage);

    assertTrue(
        thrown
            .getMessage()
            .matches(
                "Job: 'testrep' failed."
                    + " Reason: BackoffLimitExceeded."
                    + " Details: Job has reached the specified backoff limit."
                    + " Additional Details: Pod: 'testrepvmfv2-l1-3fd3c0443d46e3ac-bgdzw' had errors.\n"
                    + " Container: 'testrepvmfv2-l1' exited with code: 1.\n"
                    + " Status: Error.\n"
                    + " Logs: fatal error"));
  }

  @DisplayName(
      "parameterized test for checking how property file contents are set in the stage context on a runjob failure")
  @ParameterizedTest(name = "{index} ==> isPropertyFileContentsEmpty = {0}")
  @ValueSource(booleans = {true, false})
  void testPropertyFileContentsHandlingForK8sRunJobFailures(boolean isPropertyFileContentsEmpty)
      throws IOException {
    // setup
    when(mockKatoRestService.collectJob(any(), any(), any(), any()))
        .thenReturn(
            createJobStatusFromResource(
                "clouddriver/tasks/job/kubernetes/failed-runjob-status.json"));

    Map<String, Object> propertyFileContents = new HashMap<>();
    if (!isPropertyFileContentsEmpty) {
      propertyFileContents.put("some key", "some value");
    }

    when(mockKatoRestService.getFileContents(
            eq("test-app"), eq("test-account"), eq("test"), eq("job testrep"), eq("testrep")))
        .thenReturn(propertyFileContents);

    StageExecution myStage =
        createStageFromResource(
            "clouddriver/tasks/job/kubernetes/runjob-stage-context-with-property-file.json");

    // when
    JobFailedException thrown = assertThrows(JobFailedException.class, () -> task.execute(myStage));

    // then
    verify(mockKatoRestService, times(1))
        .collectJob(eq("test-app"), eq("test-account"), eq("test"), eq("job testrep"));

    verify(mockKatoRestService, times(1))
        .getFileContents(
            eq("test-app"), eq("test-account"), eq("test"), eq("job testrep"), eq("testrep"));

    verifyNoInteractions(mockFront50Service);

    // check to validate that we update the execution details in the repository before raising an
    // exception
    verify(mockExecutionRepository, times(1)).storeStage(myStage);

    // validate that depending on the response obtained from the getFileContents() call, we either
    // set propertyFileContents in the stage context or not
    if (isPropertyFileContentsEmpty) {
      assertThat(myStage.getContext().containsKey("propertyFileContents")).isFalse();
    } else {
      assertThat(myStage.getContext().containsKey("propertyFileContents")).isTrue();
      assertThat(myStage.getContext().get("propertyFileContents")).isEqualTo(propertyFileContents);
    }

    assertTrue(
        thrown
            .getMessage()
            .matches(
                "Job: 'testrep' failed."
                    + " Reason: BackoffLimitExceeded."
                    + " Details: Job has reached the specified backoff limit."
                    + " Additional Details: Pod: 'testrepvmfv2-l1-3fd3c0443d46e3ac-bgdzw' had errors.\n"
                    + " Container: 'testrepvmfv2-l1' exited with code: 1.\n"
                    + " Status: Error.\n"
                    + " Logs: fatal error"));
  }

  @Test
  void testPropertyFileContentsErrorHandlingForK8sRunJobFailures() throws IOException {
    // setup
    when(mockKatoRestService.collectJob(any(), any(), any(), any()))
        .thenReturn(
            createJobStatusFromResource(
                "clouddriver/tasks/job/kubernetes/failed-runjob-status.json"));

    when(mockKatoRestService.getFileContents(
            eq("test-app"), eq("test-account"), eq("test"), eq("job testrep"), eq("testrep")))
        .thenThrow(new RuntimeException("some exception"));

    StageExecution myStage =
        createStageFromResource(
            "clouddriver/tasks/job/kubernetes/runjob-stage-context-with-property-file.json");

    // when
    JobFailedException thrown = assertThrows(JobFailedException.class, () -> task.execute(myStage));

    // then
    verify(mockKatoRestService, times(1))
        .collectJob(eq("test-app"), eq("test-account"), eq("test"), eq("job testrep"));

    verify(
            mockKatoRestService,
            times(
                configProperties
                    .getWaitOnJobCompletionTask()
                    .getFileContentRetry()
                    .getMaxAttempts()))
        .getFileContents(
            eq("test-app"), eq("test-account"), eq("test"), eq("job testrep"), eq("testrep"));

    verifyNoInteractions(mockFront50Service);

    // check to validate that we update the execution details in the repository before raising an
    // exception
    verify(mockExecutionRepository, times(1)).storeStage(myStage);

    // even though property file content retrieval threw an exception, the final exception from this
    // test should still
    // be the real one containing the job failure details
    assertTrue(
        thrown
            .getMessage()
            .matches(
                "Job: 'testrep' failed."
                    + " Reason: BackoffLimitExceeded."
                    + " Details: Job has reached the specified backoff limit."
                    + " Additional Details: Pod: 'testrepvmfv2-l1-3fd3c0443d46e3ac-bgdzw' had errors.\n"
                    + " Container: 'testrepvmfv2-l1' exited with code: 1.\n"
                    + " Status: Error.\n"
                    + " Logs: fatal error"));
  }

  private StageExecutionImpl createStageWithContext(Map<String, ?> context) {
    return new StageExecutionImpl(
        new PipelineExecutionImpl(ExecutionType.PIPELINE, "test-app"),
        "test",
        new HashMap<>(context));
  }

  private StageExecutionImpl createStageWithContextWithoutExecutionApplication(
      Map<String, ?> context) {
    return new StageExecutionImpl(
        new PipelineExecutionImpl(ExecutionType.PIPELINE, null), "test", new HashMap<>(context));
  }

  private StageExecution createStageFromResource(String resourceName) {
    Map<String, Object> context = getResource(objectMapper, resourceName, Map.class);
    return createStageWithContext(context);
  }

  private Response createJobStatusFromResource(String resourceName) throws IOException {
    InputStream jobStatusInputStream = getResourceAsStream(resourceName);

    return new Response(
        "test-url",
        200,
        "test-reason",
        Collections.emptyList(),
        new TypedByteArray("application/json", IOUtils.toByteArray(jobStatusInputStream)));
  }

  @DisplayName(
      "parameterized test to see how keys in the outputs object are filtered based on the inputs")
  @ParameterizedTest(name = "{index} ==> keys to be excluded from outputs = {0}")
  @ValueSource(strings = {"", "jobStatus,completionDetails"})
  void testOutputFilter(String keysToFilter) throws IOException {
    // setup
    Set<String> expectedKeysToBeExcludedFromOutput = new HashSet<>();
    if (!keysToFilter.equals("")) {
      expectedKeysToBeExcludedFromOutput = new HashSet<>(Arrays.asList(keysToFilter.split(",")));
    }

    configProperties
        .getWaitOnJobCompletionTask()
        .setExcludeKeysFromOutputs(expectedKeysToBeExcludedFromOutput);

    // when
    when(mockKatoRestService.collectJob("test-app", "test-account", "test", "job testrep"))
        .thenReturn(
            createJobStatusFromResource(
                "clouddriver/tasks/job/kubernetes/successful-runjob-status.json"));

    when(mockKatoRestService.getFileContents(
            "test-app", "test-account", "test", "job testrep", "testrep"))
        .thenReturn(Map.of("some-key", "some-value"));

    TaskResult result =
        task.execute(
            createStageFromResource(
                "clouddriver/tasks/job/kubernetes/runjob-stage-context-with-property-file.json"));

    // then
    assertThat(result.getOutputs()).isNotEmpty();
    assertThat(result.getContext()).isNotEmpty();

    // the 'outputs' key should not contain the values present in the input i.e. in `keysToFilter`
    Set<String> receivedOutputsKeySet = result.getOutputs().keySet();
    for (String excludedKey : expectedKeysToBeExcludedFromOutput) {
      assertThat(receivedOutputsKeySet.contains(excludedKey)).isFalse();
    }

    // ensuring that the 'context' key still has the values present in the input i.e. in
    // `keysToFilter`
    Set<String> receivedContextKeySet = result.getContext().keySet();
    for (String excludedKey : expectedKeysToBeExcludedFromOutput) {
      assertThat(receivedContextKeySet.contains(excludedKey)).isTrue();
    }
  }
}
