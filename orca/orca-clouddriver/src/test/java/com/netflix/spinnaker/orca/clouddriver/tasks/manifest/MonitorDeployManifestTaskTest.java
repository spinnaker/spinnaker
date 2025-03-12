/*
 * Copyright 2021 Salesforce, Inc.
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

package com.netflix.spinnaker.orca.clouddriver.tasks.manifest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.netflix.spectator.api.NoopRegistry;
import com.netflix.spectator.api.Registry;
import com.netflix.spinnaker.kork.core.RetrySupport;
import com.netflix.spinnaker.kork.dynamicconfig.DynamicConfigService;
import com.netflix.spinnaker.kork.retrofit.exceptions.SpinnakerHttpException;
import com.netflix.spinnaker.kork.retrofit.exceptions.SpinnakerServerException;
import com.netflix.spinnaker.orca.api.pipeline.TaskResult;
import com.netflix.spinnaker.orca.api.pipeline.models.ExecutionStatus;
import com.netflix.spinnaker.orca.api.pipeline.models.ExecutionType;
import com.netflix.spinnaker.orca.api.pipeline.models.TaskExecution;
import com.netflix.spinnaker.orca.clouddriver.KatoService;
import com.netflix.spinnaker.orca.clouddriver.OortService;
import com.netflix.spinnaker.orca.clouddriver.model.Manifest;
import com.netflix.spinnaker.orca.clouddriver.model.Task;
import com.netflix.spinnaker.orca.clouddriver.model.TaskId;
import com.netflix.spinnaker.orca.clouddriver.model.TaskOwner;
import com.netflix.spinnaker.orca.pipeline.model.PipelineExecutionImpl;
import com.netflix.spinnaker.orca.pipeline.model.StageExecutionImpl;
import com.netflix.spinnaker.orca.pipeline.model.TaskExecutionImpl;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.assertj.core.api.AssertionsForClassTypes;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.annotation.DirtiesContext;
import retrofit.RetrofitError;
import retrofit.client.Response;
import retrofit.converter.JacksonConverter;
import retrofit.mime.TypedByteArray;

@ExtendWith(MockitoExtension.class)
@DirtiesContext(methodMode = DirtiesContext.MethodMode.AFTER_METHOD)
final class MonitorDeployManifestTaskTest {

  private static final String UNSTABLE_MESSAGE = "manifest is unstable";
  private static final String FAILED_MESSAGE = "manifest failed";

  private static final String ACCOUNT = "my-account";
  private static final String MANIFEST_1 = "my-manifest-1";

  private final TaskOwner taskOwner = new TaskOwner();

  private MonitorDeployManifestTask task;
  @Mock private KatoService katoService;

  @Mock private OortService oortService;

  @Mock private DynamicConfigService dynamicConfigService;

  @Spy private RetrySupport retrySupport;

  private final Registry noopRegistry = new NoopRegistry();

  private final ObjectMapper objectMapper = new ObjectMapper();

  private final JacksonConverter jacksonConverter = new JacksonConverter(objectMapper);

  private Task katoTask;

  private StageExecutionImpl myStage;

  @BeforeEach
  void setUp() {
    task =
        new MonitorDeployManifestTask(
            katoService, oortService, noopRegistry, dynamicConfigService, retrySupport);
    katoTask = new Task("2", new Task.Status(false, false, false), List.of(), List.of(), List.of());

    myStage =
        createStageWithContext(
            ImmutableMap.of(
                "account.name",
                ACCOUNT,
                "outputs.manifestNamesByNamespace",
                ImmutableList.of(MANIFEST_1),
                "kato.last.task.id",
                new TaskId("2"),
                "cloudProvider",
                "kubernetes"));
    TaskExecution deployManifestTask = new TaskExecutionImpl();
    deployManifestTask.setId("1");
    deployManifestTask.setName(DeployManifestTask.TASK_NAME);
    deployManifestTask.setStartTime(Instant.now().minusMillis(390000L).toEpochMilli());
    deployManifestTask.setImplementingClass(DeployManifestTask.class.getSimpleName());
    deployManifestTask.setStatus(ExecutionStatus.SUCCEEDED);
    TaskExecution monitorTask = new TaskExecutionImpl();
    monitorTask.setId("2");
    monitorTask.setStartTime(Instant.now().minusMillis(360000L).toEpochMilli());
    monitorTask.setImplementingClass(MonitorDeployManifestTask.class.getSimpleName());
    monitorTask.setStatus(ExecutionStatus.RUNNING);
    monitorTask.setName(MonitorDeployManifestTask.TASK_NAME);
    myStage.setTasks(List.of(deployManifestTask, monitorTask));

    taskOwner.setName("owner-clouddriver-pod-name");
  }

  @Test
  void retryKubernetesTaskFeatureFlagDisabled() {
    when(katoService.lookupTask("2", false)).thenReturn(katoTask);

    when(dynamicConfigService.isEnabled(
            "tasks.monitor-kato-task.kubernetes.deploy-manifest.retry-task", false))
        .thenReturn(false);

    TaskResult result = task.execute(myStage);
    AssertionsForClassTypes.assertThat(result.getStatus()).isEqualTo(ExecutionStatus.RUNNING);
    verifyNoMoreInteractions(katoService);
    verifyNoInteractions(oortService);
  }

  @Test
  void maxPeriodOfInactivityNotExceeded() {
    mockRetryProperties();
    when(dynamicConfigService.getConfig(
            Long.class,
            "tasks.monitor-kato-task.kubernetes.deploy-manifest.maximum-period-inactivity-ms",
            300000L))
        .thenReturn(600000L);
    when(katoService.lookupTask("2", false)).thenReturn(katoTask);

    TaskResult result = task.execute(myStage);
    AssertionsForClassTypes.assertThat(result.getStatus()).isEqualTo(ExecutionStatus.RUNNING);
    verifyNoMoreInteractions(katoService);
    verifyNoInteractions(oortService);
  }

  @Test
  void lookupTaskOwnerCallFails() {
    mockRetryProperties();
    when(katoService.lookupTask("2", false)).thenReturn(katoTask);
    when(katoService.lookupTaskOwner("kubernetes", "2")).thenThrow(SpinnakerServerException.class);

    TaskResult result = task.execute(myStage);
    AssertionsForClassTypes.assertThat(result.getStatus()).isEqualTo(ExecutionStatus.RUNNING);
    verify(katoService).lookupTaskOwner("kubernetes", "2");
    verifyNoMoreInteractions(katoService);
    verifyNoInteractions(oortService);

    assertThat(myStage.getContext().containsKey("kato.task.forceRetryFatalError")).isTrue();
    assertThat(myStage.getContext().get("kato.task.forceRetryFatalError")).isEqualTo(true);
  }

  @Test
  void previousRetryAttemptsFailedWithAFatalError() {
    mockRetryProperties();
    when(katoService.lookupTask("2", false)).thenReturn(katoTask);
    when(katoService.lookupTaskOwner("kubernetes", "2")).thenThrow(SpinnakerServerException.class);

    TaskResult result = task.execute(myStage);
    AssertionsForClassTypes.assertThat(result.getStatus()).isEqualTo(ExecutionStatus.RUNNING);
    verify(katoService).lookupTaskOwner("kubernetes", "2");
    verifyNoMoreInteractions(katoService);
    verifyNoInteractions(oortService);
    assertThat(myStage.getContext().containsKey("kato.task.forceRetryFatalError")).isTrue();
    assertThat(myStage.getContext().get("kato.task.forceRetryFatalError")).isEqualTo(true);

    TaskResult result2 = task.execute(myStage);
    AssertionsForClassTypes.assertThat(result2.getStatus()).isEqualTo(ExecutionStatus.RUNNING);
    verifyNoMoreInteractions(katoService);
    verifyNoInteractions(oortService);
  }

  @Test
  void clouddriverOwnerPodIsAlive() {
    mockRetryProperties();
    when(katoService.lookupTask("2", false)).thenReturn(katoTask);
    when(katoService.lookupTaskOwner("kubernetes", "2")).thenReturn(taskOwner);

    when(dynamicConfigService.getConfig(
            String.class, "tasks.monitor-kato-task.kubernetes.deploy-manifest.account", ""))
        .thenReturn("account");

    when(dynamicConfigService.getConfig(
            String.class,
            "tasks.monitor-kato-task.kubernetes.deploy-manifest.namespace",
            "spinnaker"))
        .thenReturn("ns");

    when(oortService.getManifest("account", "ns", "pod owner-clouddriver-pod-name", false))
        .thenReturn(manifestBuilder().stable(true).failed(false).build());

    TaskResult result = task.execute(myStage);
    AssertionsForClassTypes.assertThat(result.getStatus()).isEqualTo(ExecutionStatus.RUNNING);
    verify(katoService).lookupTaskOwner("kubernetes", "2");
    verify(oortService).getManifest("account", "ns", "pod owner-clouddriver-pod-name", false);
    verifyNoMoreInteractions(katoService);
  }

  @Test
  void clouddriverOwnerPodManifestLookupResultsInError() {
    mockRetryProperties();
    when(katoService.lookupTask("2", false)).thenReturn(katoTask);
    when(katoService.lookupTaskOwner("kubernetes", "2")).thenReturn(taskOwner);

    when(dynamicConfigService.getConfig(
            String.class, "tasks.monitor-kato-task.kubernetes.deploy-manifest.account", ""))
        .thenReturn("account");

    when(dynamicConfigService.getConfig(
            String.class,
            "tasks.monitor-kato-task.kubernetes.deploy-manifest.namespace",
            "spinnaker"))
        .thenReturn("ns");

    when(oortService.getManifest("account", "ns", "pod owner-clouddriver-pod-name", false))
        .thenThrow(SpinnakerHttpException.class);

    TaskResult result = task.execute(myStage);
    AssertionsForClassTypes.assertThat(result.getStatus()).isEqualTo(ExecutionStatus.RUNNING);
    verify(katoService).lookupTaskOwner("kubernetes", "2");
    verify(oortService).getManifest("account", "ns", "pod owner-clouddriver-pod-name", false);
    verifyNoMoreInteractions(katoService);
  }

  @Test
  void noClouddriverOwnerPodManifestFound() {
    mockRetryProperties();
    myStage.getContext().put("kato.task.retriedOperation", true);
    myStage.getContext().put("kato.task.forcedRetries", 0);

    ImmutableMap<String, Map> operation = task.getOperation(myStage);
    assertThat(ImmutableList.of(operation)).isNotNull();

    when(katoService.lookupTask("2", false)).thenReturn(katoTask);
    when(katoService.lookupTaskOwner("kubernetes", "2")).thenReturn(taskOwner);

    when(dynamicConfigService.getConfig(
            String.class, "tasks.monitor-kato-task.kubernetes.deploy-manifest.account", ""))
        .thenReturn("account");
    when(dynamicConfigService.getConfig(
            String.class,
            "tasks.monitor-kato-task.kubernetes.deploy-manifest.namespace",
            "spinnaker"))
        .thenReturn("ns");

    when(oortService.getManifest("account", "ns", "pod owner-clouddriver-pod-name", false))
        .thenThrow(notFoundError());

    TaskResult result = task.execute(myStage);
    AssertionsForClassTypes.assertThat(result.getStatus()).isEqualTo(ExecutionStatus.RUNNING);
    verify(katoService).lookupTaskOwner("kubernetes", "2");
    verify(oortService).getManifest("account", "ns", "pod owner-clouddriver-pod-name", false);

    verify(katoService).updateTaskRetryability("kubernetes", "2", true);
    verify(katoService).restartTask("kubernetes", "2", ImmutableList.of(operation));

    // verify that the retry count was incremented
    assertThat(myStage.getContext().get("kato.task.forcedRetries")).isEqualTo(1);
  }

  @Test
  void clouddriverOwnerPodManifestFoundButUpdateTaskRetryabilityFails() {
    mockRetryProperties();
    myStage.getContext().put("kato.task.retriedOperation", true);
    myStage.getContext().put("kato.task.forcedRetries", 0);

    ImmutableMap<String, Map> operation = task.getOperation(myStage);
    assertThat(ImmutableList.of(operation)).isNotNull();

    when(katoService.lookupTask("2", false)).thenReturn(katoTask);
    when(katoService.lookupTaskOwner("kubernetes", "2")).thenReturn(taskOwner);
    when(katoService.updateTaskRetryability("kubernetes", "2", true))
        .thenThrow(
            SpinnakerServerException
                .class); // arbitrary exception since the idea is to swallow all errors

    when(dynamicConfigService.getConfig(
            String.class, "tasks.monitor-kato-task.kubernetes.deploy-manifest.account", ""))
        .thenReturn("account");
    when(dynamicConfigService.getConfig(
            String.class,
            "tasks.monitor-kato-task.kubernetes.deploy-manifest.namespace",
            "spinnaker"))
        .thenReturn("ns");

    when(oortService.getManifest("account", "ns", "pod owner-clouddriver-pod-name", false))
        .thenThrow(notFoundError());

    TaskResult result = task.execute(myStage);
    AssertionsForClassTypes.assertThat(result.getStatus()).isEqualTo(ExecutionStatus.RUNNING);
    verify(katoService).lookupTaskOwner("kubernetes", "2");
    verify(oortService).getManifest("account", "ns", "pod owner-clouddriver-pod-name", false);

    verify(katoService).updateTaskRetryability("kubernetes", "2", true);
    verifyNoMoreInteractions(katoService);

    // verify that the retry count was not incremented
    assertThat(myStage.getContext().get("kato.task.forcedRetries")).isEqualTo(0);

    assertThat(myStage.getContext().containsKey("kato.task.forceRetryFatalError")).isTrue();
    assertThat(myStage.getContext().get("kato.task.forceRetryFatalError")).isEqualTo(true);
  }

  @Test
  void clouddriverOwnerPodManifestFoundButForceTaskRetryFails() {
    mockRetryProperties();
    myStage.getContext().put("kato.task.retriedOperation", true);
    myStage.getContext().put("kato.task.forcedRetries", 0);

    ImmutableMap<String, Map> operation = task.getOperation(myStage);
    assertThat(ImmutableList.of(operation)).isNotNull();

    when(katoService.lookupTask("2", false)).thenReturn(katoTask);
    when(katoService.lookupTaskOwner("kubernetes", "2")).thenReturn(taskOwner);
    when(katoService.restartTask("kubernetes", "2", ImmutableList.of(operation)))
        .thenThrow(
            SpinnakerServerException
                .class); // arbitrary exception since the idea is to swallow all errors

    when(dynamicConfigService.getConfig(
            String.class, "tasks.monitor-kato-task.kubernetes.deploy-manifest.account", ""))
        .thenReturn("account");
    when(dynamicConfigService.getConfig(
            String.class,
            "tasks.monitor-kato-task.kubernetes.deploy-manifest.namespace",
            "spinnaker"))
        .thenReturn("ns");

    when(oortService.getManifest("account", "ns", "pod owner-clouddriver-pod-name", false))
        .thenThrow(notFoundError());

    TaskResult result = task.execute(myStage);
    AssertionsForClassTypes.assertThat(result.getStatus()).isEqualTo(ExecutionStatus.RUNNING);
    verify(katoService).lookupTaskOwner("kubernetes", "2");
    verify(oortService).getManifest("account", "ns", "pod owner-clouddriver-pod-name", false);

    verify(katoService).updateTaskRetryability("kubernetes", "2", true);

    verify(katoService).restartTask("kubernetes", "2", ImmutableList.of(operation));

    // verify that the retry count was not incremented
    assertThat(myStage.getContext().get("kato.task.forcedRetries")).isEqualTo(0);

    assertThat(myStage.getContext().containsKey("kato.task.forceRetryFatalError")).isTrue();
    assertThat(myStage.getContext().get("kato.task.forceRetryFatalError")).isEqualTo(true);
  }

  private void mockRetryProperties() {
    when(dynamicConfigService.isEnabled(
            "tasks.monitor-kato-task.kubernetes.deploy-manifest.retry-task", false))
        .thenReturn(true);

    when(dynamicConfigService.getConfig(
            Integer.class,
            "tasks.monitor-kato-task.kubernetes.deploy-manifest.maximum-forced-retries",
            3))
        .thenReturn(3);

    when(dynamicConfigService.getConfig(
            Long.class,
            "tasks.monitor-kato-task.kubernetes.deploy-manifest.maximum-period-inactivity-ms",
            300000L))
        .thenReturn(300000L);
  }

  private StageExecutionImpl createStageWithContext(Map<String, ?> context) {
    return new StageExecutionImpl(
        new PipelineExecutionImpl(ExecutionType.PIPELINE, "test"), "test", new HashMap<>(context));
  }

  private static ManifestBuilder manifestBuilder() {
    return new ManifestBuilder();
  }

  private static class ManifestBuilder {
    private static final Manifest.Condition UNSTABLE =
        new Manifest.Condition(false, UNSTABLE_MESSAGE);
    private static final Manifest.Condition FAILED = new Manifest.Condition(true, FAILED_MESSAGE);

    private boolean stable;
    private boolean failed;

    ManifestBuilder stable(boolean state) {
      stable = state;
      return this;
    }

    ManifestBuilder failed(boolean state) {
      failed = state;
      return this;
    }

    private Manifest.Status getStatus() {
      Manifest.Condition stableCondition = stable ? Manifest.Condition.emptyTrue() : UNSTABLE;
      Manifest.Condition failedCondition = failed ? FAILED : Manifest.Condition.emptyFalse();
      return Manifest.Status.builder().stable(stableCondition).failed(failedCondition).build();
    }

    public Manifest build() {
      return Manifest.builder().status(getStatus()).build();
    }
  }

  private SpinnakerHttpException notFoundError() {
    return new SpinnakerHttpException(
        RetrofitError.httpError(
            "http://localhost",
            new Response(
                "http://localhost",
                404,
                "Manifest (account: account, location: ns, name: pod spin-clouddriver-6df9f7768c-zzr2t) not found",
                List.of(),
                new TypedByteArray(
                    "application/json",
                    "{\"error\":\"Not Found\",\"message\":\"Manifest (account: k8s-spinnaker1-v2-account, location: spinnaker, name: spin-clouddriver-6df9f7768c-zzr2t) not found\",\"status\":404,\"timestamp\":\"2021-01-25T18:29:59.277+00:00\"}"
                        .getBytes())),
            jacksonConverter,
            Task.class));
  }
}
