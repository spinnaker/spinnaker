/*
 * Copyright 2021 Armory, Inc.
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.netflix.spinnaker.orca.api.pipeline.TaskResult;
import com.netflix.spinnaker.orca.api.pipeline.models.ExecutionType;
import com.netflix.spinnaker.orca.pipeline.model.PipelineExecutionImpl;
import com.netflix.spinnaker.orca.pipeline.model.StageExecutionImpl;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.platform.runner.JUnitPlatform;
import org.junit.runner.RunWith;

@RunWith(JUnitPlatform.class)
final class ResolveDeploySourceManifestTaskTest {

  private static final Map<Object, Object> MANIFEST_1 =
      ImmutableMap.of("test-key-1", "test-value-1");
  private static final Map<Object, Object> MANIFEST_2 =
      ImmutableMap.of("test-key-2", "test-value-2");

  @Test
  void manifestsWithObjectsIsNotFlattened() {
    ManifestEvaluator manifestEvaluator = mock(ManifestEvaluator.class);
    ResolveDeploySourceManifestTask task = new ResolveDeploySourceManifestTask(manifestEvaluator);

    StageExecutionImpl myStage = createStageWithManifests(ImmutableList.of(MANIFEST_1, MANIFEST_2));

    DeployManifestContext deployManifestContext =
        DeployManifestContext.builder().manifests(ImmutableList.of(MANIFEST_1, MANIFEST_2)).build();

    when(manifestEvaluator.evaluate(any(), eq(deployManifestContext)))
        .thenReturn(
            new ManifestEvaluator.Result(
                ImmutableList.of(MANIFEST_1, MANIFEST_2), ImmutableList.of(), ImmutableList.of()));

    TaskResult result = task.execute(myStage);
    verify(manifestEvaluator, times(1)).evaluate(any(), eq(deployManifestContext));
    assertThat(getManifests(result)).containsExactly(MANIFEST_1, MANIFEST_2);
  }

  @Test
  void manifestsWithListIsFlattened() {
    ManifestEvaluator manifestEvaluator = mock(ManifestEvaluator.class);
    ResolveDeploySourceManifestTask task = new ResolveDeploySourceManifestTask(manifestEvaluator);

    StageExecutionImpl myStage =
        createStageWithManifests(ImmutableList.of(ImmutableList.of(MANIFEST_1, MANIFEST_2)));

    DeployManifestContext deployManifestContext =
        DeployManifestContext.builder().manifests(ImmutableList.of(MANIFEST_1, MANIFEST_2)).build();

    when(manifestEvaluator.evaluate(any(), eq(deployManifestContext)))
        .thenReturn(
            new ManifestEvaluator.Result(
                ImmutableList.of(MANIFEST_1, MANIFEST_2), ImmutableList.of(), ImmutableList.of()));

    TaskResult result = task.execute(myStage);
    verify(manifestEvaluator, times(1)).evaluate(any(), eq(deployManifestContext));
    assertThat(getManifests(result)).containsExactly(MANIFEST_1, MANIFEST_2);
  }

  @Test
  void manifestsWithListsAndObjectsIsFlattened() {
    ManifestEvaluator manifestEvaluator = mock(ManifestEvaluator.class);
    ResolveDeploySourceManifestTask task = new ResolveDeploySourceManifestTask(manifestEvaluator);

    StageExecutionImpl myStage =
        createStageWithManifests(ImmutableList.of(ImmutableList.of(MANIFEST_1), MANIFEST_2));

    DeployManifestContext deployManifestContext =
        DeployManifestContext.builder().manifests(ImmutableList.of(MANIFEST_1, MANIFEST_2)).build();

    when(manifestEvaluator.evaluate(any(), eq(deployManifestContext)))
        .thenReturn(
            new ManifestEvaluator.Result(
                ImmutableList.of(MANIFEST_1, MANIFEST_2), ImmutableList.of(), ImmutableList.of()));

    TaskResult result = task.execute(myStage);
    verify(manifestEvaluator, times(1)).evaluate(any(), eq(deployManifestContext));
    assertThat(getManifests(result)).containsExactly(MANIFEST_1, MANIFEST_2);
  }

  @Test
  void manifestsWithListsIsFlattened() {
    ManifestEvaluator manifestEvaluator = mock(ManifestEvaluator.class);
    ResolveDeploySourceManifestTask task = new ResolveDeploySourceManifestTask(manifestEvaluator);

    StageExecutionImpl myStage =
        createStageWithManifests(
            ImmutableList.of(ImmutableList.of(MANIFEST_1), ImmutableList.of(MANIFEST_2)));

    DeployManifestContext deployManifestContext =
        DeployManifestContext.builder().manifests(ImmutableList.of(MANIFEST_1, MANIFEST_2)).build();

    when(manifestEvaluator.evaluate(any(), eq(deployManifestContext)))
        .thenReturn(
            new ManifestEvaluator.Result(
                ImmutableList.of(MANIFEST_1, MANIFEST_2), ImmutableList.of(), ImmutableList.of()));

    TaskResult result = task.execute(myStage);
    verify(manifestEvaluator, times(1)).evaluate(any(), eq(deployManifestContext));
    assertThat(getManifests(result)).containsExactly(MANIFEST_1, MANIFEST_2);
  }

  private StageExecutionImpl createStageWithManifests(ImmutableList<Object> manifestsByNamespace) {
    return new StageExecutionImpl(
        new PipelineExecutionImpl(ExecutionType.PIPELINE, "test"),
        "test",
        new HashMap<>(ImmutableMap.of("manifests", manifestsByNamespace)));
  }

  private static List<Map<Object, Object>> getManifests(TaskResult result) {
    Map<String, ?> context = result.getContext();
    return Optional.ofNullable(context)
        .map(c -> (List<Map<Object, Object>>) c.get("manifests"))
        .orElse(ImmutableList.of());
  }
}
