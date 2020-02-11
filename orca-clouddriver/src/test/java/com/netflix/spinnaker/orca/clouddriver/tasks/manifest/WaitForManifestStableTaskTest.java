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

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.netflix.spinnaker.orca.ExecutionStatus;
import com.netflix.spinnaker.orca.TaskResult;
import com.netflix.spinnaker.orca.clouddriver.OortService;
import com.netflix.spinnaker.orca.clouddriver.model.Manifest;
import com.netflix.spinnaker.orca.pipeline.model.Execution;
import com.netflix.spinnaker.orca.pipeline.model.Stage;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.assertj.core.api.AssertionsForClassTypes;
import org.junit.jupiter.api.Test;
import org.junit.platform.runner.JUnitPlatform;
import org.junit.runner.RunWith;

@RunWith(JUnitPlatform.class)
final class WaitForManifestStableTaskTest {
  private static final String UNSTABLE_MESSAGE = "manifest is unstable";
  private static final String FAILED_MESSAGE = "manifest failed";

  private static final String ACCOUNT = "my-account";
  private static final String NAMESPACE = "my-namespace";
  private static final String MANIFEST_1 = "my-manifest-1";
  private static final String MANIFEST_2 = "my-manifest-2";

  @Test
  void terminalWhenFailedStable() {
    OortService oortService = mock(OortService.class);
    WaitForManifestStableTask task = new WaitForManifestStableTask(oortService);

    Stage myStage =
        createStageWithManifests(ImmutableMap.of(NAMESPACE, ImmutableList.of(MANIFEST_1)));

    when(oortService.getManifest(ACCOUNT, NAMESPACE, MANIFEST_1, false))
        .thenReturn(manifestBuilder().stable(true).failed(true).build());

    TaskResult result = task.execute(myStage);
    AssertionsForClassTypes.assertThat(result.getStatus()).isEqualTo(ExecutionStatus.TERMINAL);
    assertThat(getMessages(result)).containsExactly(failedMessage(MANIFEST_1));
    assertThat(getErrors(result)).containsExactly(failedMessage(MANIFEST_1));
  }

  @Test
  void terminalWhenFailedUnstable() {
    OortService oortService = mock(OortService.class);
    WaitForManifestStableTask task = new WaitForManifestStableTask(oortService);

    Stage myStage =
        createStageWithManifests(ImmutableMap.of(NAMESPACE, ImmutableList.of(MANIFEST_1)));

    when(oortService.getManifest(ACCOUNT, NAMESPACE, MANIFEST_1, false))
        .thenReturn(manifestBuilder().stable(false).failed(true).build());

    TaskResult result = task.execute(myStage);
    AssertionsForClassTypes.assertThat(result.getStatus()).isEqualTo(ExecutionStatus.TERMINAL);
    assertThat(getMessages(result)).containsExactly(failedMessage(MANIFEST_1));
    assertThat(getErrors(result)).containsExactly(failedMessage(MANIFEST_1));
  }

  @Test
  void runningWhenUnstable() {
    OortService oortService = mock(OortService.class);
    WaitForManifestStableTask task = new WaitForManifestStableTask(oortService);

    Stage myStage =
        createStageWithManifests(ImmutableMap.of(NAMESPACE, ImmutableList.of(MANIFEST_1)));

    when(oortService.getManifest(ACCOUNT, NAMESPACE, MANIFEST_1, false))
        .thenReturn(manifestBuilder().stable(false).failed(false).build());

    TaskResult result = task.execute(myStage);
    AssertionsForClassTypes.assertThat(result.getStatus()).isEqualTo(ExecutionStatus.RUNNING);
    assertThat(getMessages(result)).containsExactly(waitingToStabilizeMessage(MANIFEST_1));
    assertThat(getErrors(result)).isEmpty();
  }

  @Test
  void succeededWhenStable() {
    OortService oortService = mock(OortService.class);
    WaitForManifestStableTask task = new WaitForManifestStableTask(oortService);

    Stage myStage =
        createStageWithManifests(ImmutableMap.of(NAMESPACE, ImmutableList.of(MANIFEST_1)));

    when(oortService.getManifest(ACCOUNT, NAMESPACE, MANIFEST_1, false))
        .thenReturn(manifestBuilder().stable(true).failed(false).build());

    TaskResult result = task.execute(myStage);
    AssertionsForClassTypes.assertThat(result.getStatus()).isEqualTo(ExecutionStatus.SUCCEEDED);
    assertThat(getMessages(result)).isEmpty();
    assertThat(getErrors(result)).isEmpty();
  }

  @Test
  void runningWhenUnknown() {
    OortService oortService = mock(OortService.class);
    WaitForManifestStableTask task = new WaitForManifestStableTask(oortService);

    Stage myStage =
        createStageWithManifests(ImmutableMap.of(NAMESPACE, ImmutableList.of(MANIFEST_1)));

    when(oortService.getManifest(ACCOUNT, NAMESPACE, MANIFEST_1, false))
        .thenReturn(manifestBuilder().build());

    TaskResult result = task.execute(myStage);
    AssertionsForClassTypes.assertThat(result.getStatus()).isEqualTo(ExecutionStatus.RUNNING);
    assertThat(getMessages(result)).containsExactly(waitingToStabilizeMessage(MANIFEST_1));
    assertThat(getErrors(result)).isEmpty();
  }

  @Test
  void doesNotRecheckManifests() {
    OortService oortService = mock(OortService.class);
    WaitForManifestStableTask task = new WaitForManifestStableTask(oortService);

    Stage myStage =
        createStageWithManifests(
            ImmutableMap.of(NAMESPACE, ImmutableList.of(MANIFEST_1, MANIFEST_2)));

    when(oortService.getManifest(ACCOUNT, NAMESPACE, MANIFEST_1, false))
        .thenReturn(manifestBuilder().stable(true).failed(false).build());
    when(oortService.getManifest(ACCOUNT, NAMESPACE, MANIFEST_2, false))
        .thenReturn(manifestBuilder().stable(false).failed(false).build());

    TaskResult result = task.execute(myStage);
    AssertionsForClassTypes.assertThat(result.getStatus()).isEqualTo(ExecutionStatus.RUNNING);

    reset(oortService);

    verify(oortService, times(0)).getManifest(ACCOUNT, NAMESPACE, MANIFEST_1, false);
    when(oortService.getManifest(ACCOUNT, NAMESPACE, MANIFEST_2, false))
        .thenReturn(manifestBuilder().stable(true).failed(false).build());

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
    WaitForManifestStableTask task = new WaitForManifestStableTask(oortService);

    Stage myStage =
        createStageWithManifests(
            ImmutableMap.of(NAMESPACE, ImmutableList.of(MANIFEST_1, MANIFEST_2)));

    when(oortService.getManifest(ACCOUNT, NAMESPACE, MANIFEST_1, false))
        .thenReturn(manifestBuilder().stable(true).failed(false).build());
    when(oortService.getManifest(ACCOUNT, NAMESPACE, MANIFEST_2, false))
        .thenReturn(manifestBuilder().stable(false).failed(false).build());

    TaskResult result = task.execute(myStage);
    AssertionsForClassTypes.assertThat(result.getStatus()).isEqualTo(ExecutionStatus.RUNNING);
    assertThat(getMessages(result)).containsExactly(waitingToStabilizeMessage(MANIFEST_2));
    assertThat(getErrors(result)).isEmpty();

    reset(oortService);

    when(oortService.getManifest(ACCOUNT, NAMESPACE, MANIFEST_2, false))
        .thenReturn(manifestBuilder().stable(true).failed(false).build());

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
    WaitForManifestStableTask task = new WaitForManifestStableTask(oortService);

    Stage myStage =
        createStageWithManifests(
            ImmutableMap.of(NAMESPACE, ImmutableList.of(MANIFEST_1, MANIFEST_2)));

    when(oortService.getManifest(ACCOUNT, NAMESPACE, MANIFEST_1, false))
        .thenReturn(manifestBuilder().stable(false).failed(true).build());
    when(oortService.getManifest(ACCOUNT, NAMESPACE, MANIFEST_2, false))
        .thenReturn(manifestBuilder().stable(false).failed(false).build());

    TaskResult result = task.execute(myStage);
    AssertionsForClassTypes.assertThat(result.getStatus()).isEqualTo(ExecutionStatus.RUNNING);
    assertThat(getMessages(result))
        .containsExactly(failedMessage(MANIFEST_1), waitingToStabilizeMessage(MANIFEST_2));
    assertThat(getErrors(result)).containsExactly(failedMessage(MANIFEST_1));

    reset(oortService);

    when(oortService.getManifest(ACCOUNT, NAMESPACE, MANIFEST_2, false))
        .thenReturn(manifestBuilder().stable(true).failed(false).build());

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
    WaitForManifestStableTask task = new WaitForManifestStableTask(oortService);

    Stage myStage =
        createStageWithManifests(
            ImmutableMap.of(NAMESPACE, ImmutableList.of(MANIFEST_1, MANIFEST_2)));

    when(oortService.getManifest(ACCOUNT, NAMESPACE, MANIFEST_1, false))
        .thenReturn(manifestBuilder().stable(false).failed(true).build());
    when(oortService.getManifest(ACCOUNT, NAMESPACE, MANIFEST_2, false))
        .thenReturn(manifestBuilder().build());

    TaskResult result = task.execute(myStage);
    AssertionsForClassTypes.assertThat(result.getStatus()).isEqualTo(ExecutionStatus.RUNNING);
    assertThat(getMessages(result))
        .containsExactly(failedMessage(MANIFEST_1), waitingToStabilizeMessage(MANIFEST_2));

    reset(oortService);

    when(oortService.getManifest(ACCOUNT, NAMESPACE, MANIFEST_2, false))
        .thenReturn(manifestBuilder().stable(true).failed(false).build());

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

  private static String waitingToStabilizeMessage(String manifest) {
    return String.format(
        "'%s' in '%s' for account %s: waiting for manifest to stabilize",
        manifest, NAMESPACE, ACCOUNT);
  }

  private static String failedMessage(String manifest) {
    return String.format(
        "'%s' in '%s' for account %s: manifest failed", manifest, NAMESPACE, ACCOUNT);
  }

  private Stage createStageWithManifests(
      ImmutableMap<String, ImmutableList<String>> manifestsByNamespace) {
    return new Stage(
        new Execution(Execution.ExecutionType.PIPELINE, "test"),
        "test",
        new HashMap<>(
            ImmutableMap.of(
                "account.name",
                ACCOUNT,
                "outputs.manifestNamesByNamespace",
                manifestsByNamespace)));
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

  private Stage createStageWithContext(Map<String, ?> context) {
    return new Stage(
        new Execution(Execution.ExecutionType.PIPELINE, "test"), "test", new HashMap<>(context));
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
}
