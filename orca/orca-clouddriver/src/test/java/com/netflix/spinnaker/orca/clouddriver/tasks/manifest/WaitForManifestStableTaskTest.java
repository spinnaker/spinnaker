/*
 * Copyright 2020 Google, LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
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
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.netflix.spinnaker.orca.api.pipeline.TaskResult;
import com.netflix.spinnaker.orca.api.pipeline.models.ExecutionStatus;
import com.netflix.spinnaker.orca.api.pipeline.models.ExecutionType;
import com.netflix.spinnaker.orca.clouddriver.OortService;
import com.netflix.spinnaker.orca.clouddriver.model.Manifest;
import com.netflix.spinnaker.orca.jackson.OrcaObjectMapper;
import com.netflix.spinnaker.orca.pipeline.model.PipelineExecutionImpl;
import com.netflix.spinnaker.orca.pipeline.model.StageExecutionImpl;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import org.assertj.core.api.AssertionsForClassTypes;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import retrofit2.mock.Calls;

final class WaitForManifestStableTaskTest {
  private static final String UNSTABLE_MESSAGE = "manifest is unstable";
  private static final String FAILED_MESSAGE = "manifest failed";

  private static final String ACCOUNT = "my-account";
  private static final String NAMESPACE = "my-namespace";
  private static final String MANIFEST_1 = "my-manifest-1";
  private static final String MANIFEST_2 = "my-manifest-2";

  private final ObjectMapper objectMapper = OrcaObjectMapper.getInstance();

  @Test
  void terminalWhenFailedStable() {
    OortService oortService = mock(OortService.class);
    WaitForManifestStableTask task = new WaitForManifestStableTask(oortService, objectMapper);

    StageExecutionImpl myStage =
        createStageWithManifests(ImmutableMap.of(NAMESPACE, ImmutableList.of(MANIFEST_1)));

    when(oortService.getManifest(ACCOUNT, NAMESPACE, MANIFEST_1, false))
        .thenReturn(Calls.response(manifestBuilder().stable(true).failed(true).build()));

    TaskResult result = task.execute(myStage);
    AssertionsForClassTypes.assertThat(result.getStatus()).isEqualTo(ExecutionStatus.TERMINAL);
    assertThat(getMessages(result)).containsExactly(failedMessage(MANIFEST_1));
    assertThat(getErrors(result)).containsExactly(failedMessage(MANIFEST_1));
  }

  @Test
  void terminalWhenFailedUnstable() {
    OortService oortService = mock(OortService.class);
    WaitForManifestStableTask task = new WaitForManifestStableTask(oortService, objectMapper);

    StageExecutionImpl myStage =
        createStageWithManifests(ImmutableMap.of(NAMESPACE, ImmutableList.of(MANIFEST_1)));

    when(oortService.getManifest(ACCOUNT, NAMESPACE, MANIFEST_1, false))
        .thenReturn(Calls.response(manifestBuilder().stable(false).failed(true).build()));

    TaskResult result = task.execute(myStage);
    AssertionsForClassTypes.assertThat(result.getStatus()).isEqualTo(ExecutionStatus.TERMINAL);
    assertThat(getMessages(result)).containsExactly(failedMessage(MANIFEST_1));
    assertThat(getErrors(result)).containsExactly(failedMessage(MANIFEST_1));
  }

  @Test
  void runningWhenUnstable() {
    OortService oortService = mock(OortService.class);
    WaitForManifestStableTask task = new WaitForManifestStableTask(oortService, objectMapper);

    StageExecutionImpl myStage =
        createStageWithManifests(ImmutableMap.of(NAMESPACE, ImmutableList.of(MANIFEST_1)));

    when(oortService.getManifest(ACCOUNT, NAMESPACE, MANIFEST_1, false))
        .thenReturn(Calls.response(manifestBuilder().stable(false).failed(false).build()));

    TaskResult result = task.execute(myStage);
    AssertionsForClassTypes.assertThat(result.getStatus()).isEqualTo(ExecutionStatus.RUNNING);
    assertThat(getMessages(result)).containsExactly(waitingToStabilizeMessage(MANIFEST_1));
    assertThat(getErrors(result)).isEmpty();
  }

  @Test
  void succeededWhenStable() {
    OortService oortService = mock(OortService.class);
    WaitForManifestStableTask task = new WaitForManifestStableTask(oortService, objectMapper);

    StageExecutionImpl myStage =
        createStageWithManifests(ImmutableMap.of(NAMESPACE, ImmutableList.of(MANIFEST_1)));

    when(oortService.getManifest(ACCOUNT, NAMESPACE, MANIFEST_1, false))
        .thenReturn(Calls.response(manifestBuilder().stable(true).failed(false).build()));

    TaskResult result = task.execute(myStage);
    AssertionsForClassTypes.assertThat(result.getStatus()).isEqualTo(ExecutionStatus.SUCCEEDED);
    assertThat(getMessages(result)).isEmpty();
    assertThat(getErrors(result)).isEmpty();
  }

  @Test
  void runningWhenUnknown() {
    OortService oortService = mock(OortService.class);
    WaitForManifestStableTask task = new WaitForManifestStableTask(oortService, objectMapper);

    StageExecutionImpl myStage =
        createStageWithManifests(ImmutableMap.of(NAMESPACE, ImmutableList.of(MANIFEST_1)));

    when(oortService.getManifest(ACCOUNT, NAMESPACE, MANIFEST_1, false))
        .thenReturn(Calls.response(manifestBuilder().build()));

    TaskResult result = task.execute(myStage);
    AssertionsForClassTypes.assertThat(result.getStatus()).isEqualTo(ExecutionStatus.RUNNING);
    assertThat(getMessages(result)).containsExactly(waitingToStabilizeMessage(MANIFEST_1));
    assertThat(getErrors(result)).isEmpty();
  }

  @Test
  void doesNotRecheckManifests() {
    OortService oortService = mock(OortService.class);
    WaitForManifestStableTask task = new WaitForManifestStableTask(oortService, objectMapper);

    StageExecutionImpl myStage =
        createStageWithManifests(
            ImmutableMap.of(NAMESPACE, ImmutableList.of(MANIFEST_1, MANIFEST_2)));

    when(oortService.getManifest(ACCOUNT, NAMESPACE, MANIFEST_1, false))
        .thenReturn(Calls.response(manifestBuilder().stable(true).failed(false).build()));
    when(oortService.getManifest(ACCOUNT, NAMESPACE, MANIFEST_2, false))
        .thenReturn(Calls.response(manifestBuilder().stable(false).failed(false).build()));

    TaskResult result = task.execute(myStage);
    AssertionsForClassTypes.assertThat(result.getStatus()).isEqualTo(ExecutionStatus.RUNNING);

    reset(oortService);

    verify(oortService, times(0)).getManifest(ACCOUNT, NAMESPACE, MANIFEST_1, false);
    when(oortService.getManifest(ACCOUNT, NAMESPACE, MANIFEST_2, false))
        .thenReturn(Calls.response(manifestBuilder().stable(true).failed(false).build()));

    result =
        task.execute(
            createStageWithContext(
                ImmutableMap.<String, Object>builder()
                    .putAll(myStage.getContext())
                    .putAll(result.getContext())
                    .build()));
    AssertionsForClassTypes.assertThat(result.getStatus()).isEqualTo(ExecutionStatus.SUCCEEDED);
  }

  @Test
  void waitsForMultipleManifests() {
    OortService oortService = mock(OortService.class);
    WaitForManifestStableTask task = new WaitForManifestStableTask(oortService, objectMapper);

    StageExecutionImpl myStage =
        createStageWithManifests(
            ImmutableMap.of(NAMESPACE, ImmutableList.of(MANIFEST_1, MANIFEST_2)));

    when(oortService.getManifest(ACCOUNT, NAMESPACE, MANIFEST_1, false))
        .thenReturn(Calls.response(manifestBuilder().stable(true).failed(false).build()));
    when(oortService.getManifest(ACCOUNT, NAMESPACE, MANIFEST_2, false))
        .thenReturn(Calls.response(manifestBuilder().stable(false).failed(false).build()));

    TaskResult result = task.execute(myStage);
    AssertionsForClassTypes.assertThat(result.getStatus()).isEqualTo(ExecutionStatus.RUNNING);
    assertThat(getMessages(result)).containsExactly(waitingToStabilizeMessage(MANIFEST_2));
    assertThat(getErrors(result)).isEmpty();

    reset(oortService);

    when(oortService.getManifest(ACCOUNT, NAMESPACE, MANIFEST_2, false))
        .thenReturn(Calls.response(manifestBuilder().stable(true).failed(false).build()));

    result =
        task.execute(
            createStageWithContext(
                ImmutableMap.<String, Object>builder()
                    .putAll(myStage.getContext())
                    .putAll(result.getContext())
                    .build()));
    AssertionsForClassTypes.assertThat(result.getStatus()).isEqualTo(ExecutionStatus.SUCCEEDED);
    assertThat(getMessages(result)).containsExactly(waitingToStabilizeMessage(MANIFEST_2));
    assertThat(getErrors(result)).isEmpty();
  }

  @Test
  void waitsForAllManifestsWhenOneFailed() {
    OortService oortService = mock(OortService.class);
    WaitForManifestStableTask task = new WaitForManifestStableTask(oortService, objectMapper);

    StageExecutionImpl myStage =
        createStageWithManifests(
            ImmutableMap.of(NAMESPACE, ImmutableList.of(MANIFEST_1, MANIFEST_2)));

    when(oortService.getManifest(ACCOUNT, NAMESPACE, MANIFEST_1, false))
        .thenReturn(Calls.response(manifestBuilder().stable(false).failed(true).build()));
    when(oortService.getManifest(ACCOUNT, NAMESPACE, MANIFEST_2, false))
        .thenReturn(Calls.response(manifestBuilder().stable(false).failed(false).build()));

    TaskResult result = task.execute(myStage);
    AssertionsForClassTypes.assertThat(result.getStatus()).isEqualTo(ExecutionStatus.RUNNING);
    assertThat(getMessages(result))
        .containsExactly(failedMessage(MANIFEST_1), waitingToStabilizeMessage(MANIFEST_2));
    assertThat(getErrors(result)).containsExactly(failedMessage(MANIFEST_1));

    reset(oortService);

    when(oortService.getManifest(ACCOUNT, NAMESPACE, MANIFEST_2, false))
        .thenReturn(Calls.response(manifestBuilder().stable(true).failed(false).build()));

    result =
        task.execute(
            createStageWithContext(
                ImmutableMap.<String, Object>builder()
                    .putAll(myStage.getContext())
                    .putAll(result.getContext())
                    .build()));

    AssertionsForClassTypes.assertThat(result.getStatus()).isEqualTo(ExecutionStatus.TERMINAL);
    assertThat(getMessages(result))
        .containsExactly(failedMessage(MANIFEST_1), waitingToStabilizeMessage(MANIFEST_2));
    assertThat(getErrors(result)).containsExactly(failedMessage(MANIFEST_1));
  }

  @Test
  void waitsForAllManifestsWhenOneFailedAndOneUnknown() {
    OortService oortService = mock(OortService.class);
    WaitForManifestStableTask task = new WaitForManifestStableTask(oortService, objectMapper);

    StageExecutionImpl myStage =
        createStageWithManifests(
            ImmutableMap.of(NAMESPACE, ImmutableList.of(MANIFEST_1, MANIFEST_2)));

    when(oortService.getManifest(ACCOUNT, NAMESPACE, MANIFEST_1, false))
        .thenReturn(Calls.response(manifestBuilder().stable(false).failed(true).build()));
    when(oortService.getManifest(ACCOUNT, NAMESPACE, MANIFEST_2, false))
        .thenReturn(Calls.response(manifestBuilder().build()));

    TaskResult result = task.execute(myStage);
    AssertionsForClassTypes.assertThat(result.getStatus()).isEqualTo(ExecutionStatus.RUNNING);
    assertThat(getMessages(result))
        .containsExactly(failedMessage(MANIFEST_1), waitingToStabilizeMessage(MANIFEST_2));

    reset(oortService);

    when(oortService.getManifest(ACCOUNT, NAMESPACE, MANIFEST_2, false))
        .thenReturn(Calls.response(manifestBuilder().stable(true).failed(false).build()));

    result =
        task.execute(
            createStageWithContext(
                ImmutableMap.<String, Object>builder()
                    .putAll(myStage.getContext())
                    .putAll(result.getContext())
                    .build()));
    AssertionsForClassTypes.assertThat(result.getStatus()).isEqualTo(ExecutionStatus.TERMINAL);
    assertThat(getMessages(result))
        .containsExactly(failedMessage(MANIFEST_1), waitingToStabilizeMessage(MANIFEST_2));
  }

  @Test
  void defaultTimeoutIs30Minutes() {
    OortService oortService = mock(OortService.class);
    WaitForManifestStableTask task = new WaitForManifestStableTask(oortService, objectMapper);

    StageExecutionImpl stage =
        createStageWithManifests(ImmutableMap.of(NAMESPACE, ImmutableList.of(MANIFEST_1)));

    assertThat(task.getDynamicTimeout(stage)).isEqualTo(TimeUnit.MINUTES.toMillis(30));
  }

  @Test
  void dynamicTimeoutReadsStableManifestTimeoutMinutes() {
    OortService oortService = mock(OortService.class);
    WaitForManifestStableTask task = new WaitForManifestStableTask(oortService, objectMapper);

    StageExecutionImpl stage =
        createStageWithContext(
            ImmutableMap.<String, Object>builder()
                .put("account.name", ACCOUNT)
                .put(
                    "outputs.manifestNamesByNamespace",
                    ImmutableMap.of(NAMESPACE, ImmutableList.of(MANIFEST_1)))
                .put("stableManifestTimeoutMinutes", 60)
                .build());

    assertThat(task.getDynamicTimeout(stage)).isEqualTo(TimeUnit.MINUTES.toMillis(60));
  }

  @Test
  void dynamicTimeoutFallsBackToDefaultWhenFieldAbsent() {
    OortService oortService = mock(OortService.class);
    WaitForManifestStableTask task = new WaitForManifestStableTask(oortService, objectMapper);

    StageExecutionImpl stage =
        createStageWithManifests(ImmutableMap.of(NAMESPACE, ImmutableList.of(MANIFEST_1)));

    assertThat(task.getDynamicTimeout(stage)).isEqualTo(TimeUnit.MINUTES.toMillis(30));
  }

  @Test
  void dynamicTimeoutAcceptsStringValue() {
    OortService oortService = mock(OortService.class);
    WaitForManifestStableTask task = new WaitForManifestStableTask(oortService, objectMapper);

    StageExecutionImpl stage =
        createStageWithContext(
            ImmutableMap.<String, Object>builder()
                .put("account.name", ACCOUNT)
                .put(
                    "outputs.manifestNamesByNamespace",
                    ImmutableMap.of(NAMESPACE, ImmutableList.of(MANIFEST_1)))
                .put("stableManifestTimeoutMinutes", "45")
                .build());

    assertThat(task.getDynamicTimeout(stage)).isEqualTo(TimeUnit.MINUTES.toMillis(45));
  }

  @Test
  void failureMessageFromTheClusterIsRenderedLiterally() {
    OortService oortService = mock(OortService.class);
    WaitForManifestStableTask task = new WaitForManifestStableTask(oortService, objectMapper);

    StageExecutionImpl myStage =
        createStageWithManifests(ImmutableMap.of(NAMESPACE, ImmutableList.of(MANIFEST_1)));

    String clusterMessage =
        "Degraded: [details](https://example.com)\n\n![x](https://example.com/x.png)";
    Manifest.Status status =
        Manifest.Status.builder()
            .stable(Manifest.Condition.emptyFalse())
            .failed(new Manifest.Condition(true, clusterMessage))
            .build();
    when(oortService.getManifest(ACCOUNT, NAMESPACE, MANIFEST_1, false))
        .thenReturn(Calls.response(Manifest.builder().status(status).build()));

    TaskResult result = task.execute(myStage);

    String expected =
        String.format(
            "'%s' in '%s' for account %s: `Degraded: [details](https://example.com) ![x](https://example.com/x.png)`",
            MANIFEST_1, NAMESPACE, ACCOUNT);
    AssertionsForClassTypes.assertThat(result.getStatus()).isEqualTo(ExecutionStatus.TERMINAL);
    assertThat(getMessages(result)).containsExactly(expected);
    assertThat(getErrors(result)).containsExactly(expected);
  }

  @ParameterizedTest
  @CsvSource(
      delimiter = '|',
      value = {
        "plain text | `plain text`",
        "uses `kubectl` here | ``uses `kubectl` here``",
        "```fenced``` and `one` | ```` ```fenced``` and `one` ````",
        "`starts with a tick | `` `starts with a tick ``",
        "ends with a tick` | `` ends with a tick` ``",
        "<b>html</b> & *emphasis* | `<b>html</b> & *emphasis*`",
      })
  void asInlineCodeProducesALiteralCodeSpan(String message, String expected) {
    assertThat(WaitForManifestStableTask.asInlineCode(message)).isEqualTo(expected);
  }

  @Test
  void asInlineCodeFlattensLineBreaksAndLeavesEmptyMessagesAlone() {
    assertThat(WaitForManifestStableTask.asInlineCode("first\r\n\nsecond"))
        .isEqualTo("`first second`");
    assertThat(WaitForManifestStableTask.asInlineCode(null)).isNull();
    assertThat(WaitForManifestStableTask.asInlineCode("  ")).isEqualTo("  ");
  }

  @Test
  void waitsForAllManifestsWhenOneFailedWithEvents() throws IOException {
    // setup
    String k8sDeploymentName = "deployment " + MANIFEST_1;
    String replicaSetName = "replicaSet example-service-867f486c5";
    String podName = "pod example-service-web-867f486c5-cvprp";
    OortService oortService = mock(OortService.class);
    WaitForManifestStableTask task = new WaitForManifestStableTask(oortService, objectMapper);

    StageExecutionImpl myStage =
        createStageWithManifests(
            ImmutableMap.of(NAMESPACE, ImmutableList.of(k8sDeploymentName, MANIFEST_2)), true);

    List<Object> manifestEvents =
        objectMapper.readValue(
            WaitForManifestStableTaskTest.class.getResourceAsStream(("deployment-events.json")),
            new TypeReference<>() {});
    // deployment manifest
    when(oortService.getManifest(ACCOUNT, NAMESPACE, k8sDeploymentName, true))
        .thenReturn(
            Calls.response(
                manifestBuilder()
                    .name(k8sDeploymentName)
                    .events(manifestEvents)
                    .stable(false)
                    .failed(true)
                    .build()));
    // any other K8s manifest
    when(oortService.getManifest(ACCOUNT, NAMESPACE, MANIFEST_2, true))
        .thenReturn(Calls.response(manifestBuilder().stable(false).failed(false).build()));

    // when
    TaskResult result = task.execute(myStage);

    // then
    // verify Manifest 2 hasn't stabilized yet and Manifest 1 has failed
    AssertionsForClassTypes.assertThat(result.getStatus()).isEqualTo(ExecutionStatus.RUNNING);

    assertThat(getMessages(result))
        .containsExactly(failedMessage(k8sDeploymentName), waitingToStabilizeMessage(MANIFEST_2));
    assertThat(getErrors(result)).contains(failedMessage(k8sDeploymentName));

    reset(oortService);

    // deployment manifest
    when(oortService.getManifest(ACCOUNT, NAMESPACE, k8sDeploymentName, true))
        .thenReturn(
            Calls.response(
                manifestBuilder()
                    .name(k8sDeploymentName)
                    .events(manifestEvents)
                    .stable(false)
                    .failed(true)
                    .build()));
    // now manifest 2 has stabilized
    when(oortService.getManifest(ACCOUNT, NAMESPACE, MANIFEST_2, true))
        .thenReturn(Calls.response(manifestBuilder().stable(true).failed(false).build()));

    manifestEvents =
        objectMapper.readValue(
            WaitForManifestStableTaskTest.class.getResourceAsStream(("replica-set-events.json")),
            new TypeReference<>() {});
    when(oortService.getManifest(ACCOUNT, NAMESPACE, replicaSetName, true))
        .thenReturn(
            Calls.response(
                manifestBuilder()
                    .name(replicaSetName)
                    .events(manifestEvents)
                    .stable(true)
                    .failed(false)
                    .build()));

    manifestEvents =
        objectMapper.readValue(
            WaitForManifestStableTaskTest.class.getResourceAsStream(("pod-events.json")),
            new TypeReference<>() {});
    when(oortService.getManifest(ACCOUNT, NAMESPACE, podName, true))
        .thenReturn(
            Calls.response(
                manifestBuilder()
                    .name(podName)
                    .events(manifestEvents)
                    .stable(false)
                    .failed(false)
                    .build()));

    // when:
    result =
        task.execute(
            createStageWithContext(
                ImmutableMap.<String, Object>builder()
                    .putAll(myStage.getContext())
                    .putAll(result.getContext())
                    .build()));

    // verify that we see the right messages
    AssertionsForClassTypes.assertThat(result.getStatus()).isEqualTo(ExecutionStatus.TERMINAL);
    assertThat(getMessages(result))
        .containsExactly(failedMessage(k8sDeploymentName), waitingToStabilizeMessage(MANIFEST_2));
    assertThat(getErrors(result))
        .containsExactly(
            failedMessage(k8sDeploymentName),
            String.format(
                "Resource: '%s' in '%s' for account %s is not stable. Reason: FailedScheduling."
                    + " Details: 0/7 nodes are available: 1 node(s) had untolerated taint {example.com/infra: true},"
                    + " 3 node(s) didn't match Pod's node affinity/selector, 3 node(s) had untolerated taint"
                    + " {node-role.kubernetes.io/control-plane: }."
                    + " preemption: 0/7 nodes are available: 7 Preemption is not helpful for scheduling..",
                podName, NAMESPACE, ACCOUNT));
    verify(oortService, times(0)).getManifest(ACCOUNT, NAMESPACE, k8sDeploymentName, true);
    verify(oortService, times(1)).getManifest(ACCOUNT, NAMESPACE, MANIFEST_2, true);
    verify(oortService, times(1)).getManifest(ACCOUNT, NAMESPACE, replicaSetName, true);
    verify(oortService, times(1)).getManifest(ACCOUNT, NAMESPACE, podName, true);
  }

  private static String waitingToStabilizeMessage(String manifest) {
    return String.format(
        "'%s' in '%s' for account %s: waiting for manifest to stabilize",
        manifest, NAMESPACE, ACCOUNT);
  }

  private static String failedMessage(String manifest) {
    return String.format(
        "'%s' in '%s' for account %s: `manifest failed`", manifest, NAMESPACE, ACCOUNT);
  }

  private StageExecutionImpl createStageWithManifests(
      ImmutableMap<String, ImmutableList<String>> manifestsByNamespace) {
    return createStageWithManifests(manifestsByNamespace, false);
  }

  private StageExecutionImpl createStageWithManifests(
      ImmutableMap<String, ImmutableList<String>> manifestsByNamespace, boolean includeEvents) {
    return new StageExecutionImpl(
        new PipelineExecutionImpl(ExecutionType.PIPELINE, "test"),
        "test",
        new HashMap<>(
            ImmutableMap.of(
                "account.name",
                ACCOUNT,
                "outputs.manifestNamesByNamespace",
                manifestsByNamespace,
                "includeEvents",
                includeEvents)));
  }

  @SuppressWarnings("unchecked")
  private static List<String> getMessages(TaskResult result) {
    Map<String, ?> context = result.getContext();
    return Optional.ofNullable(context)
        .map(c -> (List<String>) c.get("messages"))
        .orElse(ImmutableList.of());
  }

  @SuppressWarnings("unchecked")
  private static List<String> getErrors(TaskResult result) {
    Map<String, ?> context = result.getContext();
    return Optional.ofNullable(context)
        .map(c -> (Map<String, Object>) c.get("exception"))
        .map(e -> (Map<String, Object>) e.get("details"))
        .map(d -> (List<String>) d.get("errors"))
        .orElse(ImmutableList.of());
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

    private List<Object> eventsList = new ArrayList<>();

    private String name = "not set";

    ManifestBuilder stable(boolean state) {
      stable = state;
      return this;
    }

    ManifestBuilder failed(boolean state) {
      failed = state;
      return this;
    }

    ManifestBuilder events(List<Object> manifestEvents) {
      eventsList = manifestEvents;
      return this;
    }

    ManifestBuilder name(String name) {
      this.name = Objects.requireNonNullElse(name, "not set");
      return this;
    }

    private Manifest.Status getStatus() {
      Manifest.Condition stableCondition = stable ? Manifest.Condition.emptyTrue() : UNSTABLE;
      Manifest.Condition failedCondition = failed ? FAILED : Manifest.Condition.emptyFalse();
      return Manifest.Status.builder().stable(stableCondition).failed(failedCondition).build();
    }

    public Manifest build() {
      Map<String, Object> manifest = new HashMap<>();
      manifest.put("kind", "Deployment");
      return Manifest.builder()
          .name(name)
          .manifest(manifest)
          .status(getStatus())
          .events(eventsList)
          .build();
    }
  }
}
