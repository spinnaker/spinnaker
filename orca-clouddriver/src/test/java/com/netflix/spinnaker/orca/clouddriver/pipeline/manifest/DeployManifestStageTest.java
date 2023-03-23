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
 */

package com.netflix.spinnaker.orca.clouddriver.pipeline.manifest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.netflix.spinnaker.orca.api.pipeline.models.ExecutionType;
import com.netflix.spinnaker.orca.api.pipeline.models.StageExecution;
import com.netflix.spinnaker.orca.clouddriver.OortService;
import com.netflix.spinnaker.orca.clouddriver.model.Manifest;
import com.netflix.spinnaker.orca.clouddriver.model.ManifestCoordinates;
import com.netflix.spinnaker.orca.clouddriver.pipeline.manifest.DeployManifestStage.GetDeployedManifests;
import com.netflix.spinnaker.orca.clouddriver.pipeline.manifest.DeployManifestStage.ManifestOperationsHelper;
import com.netflix.spinnaker.orca.clouddriver.pipeline.manifest.DeployManifestStage.OldManifestActionAppender;
import com.netflix.spinnaker.orca.clouddriver.tasks.manifest.DeployManifestContext;
import com.netflix.spinnaker.orca.clouddriver.tasks.manifest.DeployManifestContext.TrafficManagement.ManifestStrategyType;
import com.netflix.spinnaker.orca.jackson.OrcaObjectMapper;
import com.netflix.spinnaker.orca.pipeline.graph.StageGraphBuilderImpl;
import com.netflix.spinnaker.orca.pipeline.model.PipelineExecutionImpl;
import com.netflix.spinnaker.orca.pipeline.model.StageExecutionImpl;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
final class DeployManifestStageTest {
  private static final ObjectMapper objectMapper = OrcaObjectMapper.getInstance();
  private static final String ACCOUNT = "my-acct";
  private static final String APPLICATION = "my-app";
  private static final String CLUSTER = "replicaSet my-rs";
  private static final String NAMESAPCE = "my-ns";

  private OortService oortService;
  private ManifestOperationsHelper manifestOperationsHelper;
  private GetDeployedManifests getDeployedManifests;
  private DeployManifestStage deployManifestStage;
  private OldManifestActionAppender oldManifestActionAppender;

  private static Map<String, Object> getContext(DeployManifestContext deployManifestContext) {
    Map<String, Object> context =
        objectMapper.convertValue(
            deployManifestContext, new TypeReference<Map<String, Object>>() {});
    context.put("moniker", ImmutableMap.of("app", APPLICATION));
    context.put("account", ACCOUNT);
    context.put(
        "outputs.manifests",
        ImmutableList.of(
            ImmutableMap.of(
                "kind",
                "ReplicaSet",
                "metadata",
                ImmutableMap.of(
                    "name",
                    "my-rs-v001",
                    "namespace",
                    NAMESAPCE,
                    "annotations",
                    ImmutableMap.of("moniker.spinnaker.io/cluster", CLUSTER)))));
    return context;
  }

  @BeforeEach
  void setUp() {
    oortService = mock(OortService.class);
    manifestOperationsHelper = new ManifestOperationsHelper(oortService);
    getDeployedManifests = new GetDeployedManifests(manifestOperationsHelper);
    oldManifestActionAppender =
        new OldManifestActionAppender(getDeployedManifests, manifestOperationsHelper);
    deployManifestStage = new DeployManifestStage(oldManifestActionAppender);
  }

  @Test
  void rolloutStrategyDisabled() {
    StageExecutionImpl stage = new StageExecutionImpl();
    stage.setContext(
        getContext(
            DeployManifestContext.builder()
                .trafficManagement(
                    DeployManifestContext.TrafficManagement.builder().enabled(false).build())
                .build()));
    assertThat(getAfterStages(stage)).isEmpty();
  }

  @Test
  void rolloutStrategyRedBlack() {
    givenManifestIsStable();
    when(oortService.getClusterManifests(ACCOUNT, NAMESAPCE, "replicaSet", APPLICATION, CLUSTER))
        .thenReturn(
            ImmutableList.of(
                ManifestCoordinates.builder()
                    .name("my-rs-v000")
                    .kind("replicaSet")
                    .namespace(NAMESAPCE)
                    .build(),
                ManifestCoordinates.builder()
                    .name("my-rs-v001")
                    .kind("replicaSet")
                    .namespace(NAMESAPCE)
                    .build()));
    Map<String, Object> context =
        getContext(
            DeployManifestContext.builder()
                .trafficManagement(
                    DeployManifestContext.TrafficManagement.builder()
                        .enabled(true)
                        .options(
                            DeployManifestContext.TrafficManagement.Options.builder()
                                .strategy(ManifestStrategyType.RED_BLACK)
                                .build())
                        .build())
                .build());
    StageExecutionImpl stage =
        new StageExecutionImpl(
            new PipelineExecutionImpl(ExecutionType.PIPELINE, APPLICATION),
            DeployManifestStage.PIPELINE_CONFIG_TYPE,
            context);
    assertThat(getAfterStages(stage))
        .extracting(StageExecution::getType)
        .containsExactly(DisableManifestStage.PIPELINE_CONFIG_TYPE);
    assertThat(getAfterStages(stage))
        .extracting(s -> s.getContext().get("account"))
        .containsExactly(ACCOUNT);
    assertThat(getAfterStages(stage))
        .extracting(s -> s.getContext().get("app"))
        .containsExactly(APPLICATION);
    assertThat(getAfterStages(stage))
        .extracting(s -> s.getContext().get("location"))
        .containsExactly(NAMESAPCE);
    assertThat(getAfterStages(stage))
        .extracting(s -> s.getContext().get("manifestName"))
        .containsExactly("replicaSet my-rs-v000");
  }

  @Test
  void rolloutStrategyBlueGreen() {
    givenManifestIsStable();
    when(oortService.getClusterManifests(ACCOUNT, NAMESAPCE, "replicaSet", APPLICATION, CLUSTER))
        .thenReturn(
            ImmutableList.of(
                ManifestCoordinates.builder()
                    .name("my-rs-v000")
                    .kind("replicaSet")
                    .namespace(NAMESAPCE)
                    .build(),
                ManifestCoordinates.builder()
                    .name("my-rs-v001")
                    .kind("replicaSet")
                    .namespace(NAMESAPCE)
                    .build()));
    Map<String, Object> context =
        getContext(
            DeployManifestContext.builder()
                .trafficManagement(
                    DeployManifestContext.TrafficManagement.builder()
                        .enabled(true)
                        .options(
                            DeployManifestContext.TrafficManagement.Options.builder()
                                .strategy(ManifestStrategyType.BLUE_GREEN)
                                .build())
                        .build())
                .build());
    StageExecutionImpl stage =
        new StageExecutionImpl(
            new PipelineExecutionImpl(ExecutionType.PIPELINE, APPLICATION),
            DeployManifestStage.PIPELINE_CONFIG_TYPE,
            context);
    assertThat(getAfterStages(stage))
        .extracting(StageExecution::getType)
        .containsExactly(DisableManifestStage.PIPELINE_CONFIG_TYPE);
    assertThat(getAfterStages(stage))
        .extracting(s -> s.getContext().get("account"))
        .containsExactly(ACCOUNT);
    assertThat(getAfterStages(stage))
        .extracting(s -> s.getContext().get("app"))
        .containsExactly(APPLICATION);
    assertThat(getAfterStages(stage))
        .extracting(s -> s.getContext().get("location"))
        .containsExactly(NAMESAPCE);
    assertThat(getAfterStages(stage))
        .extracting(s -> s.getContext().get("manifestName"))
        .containsExactly("replicaSet my-rs-v000");
  }

  @Test
  @DisplayName("blue/green deployment should trigger old manifest deletion if it was failed")
  void rolloutBlueGreenStrategyDeletesOldManifest() {
    givenManifestIsNotStable();
    when(oortService.getClusterManifests(ACCOUNT, NAMESAPCE, "replicaSet", APPLICATION, CLUSTER))
        .thenReturn(
            ImmutableList.of(
                ManifestCoordinates.builder()
                    .name("my-rs-v000")
                    .kind("replicaSet")
                    .namespace(NAMESAPCE)
                    .build(),
                ManifestCoordinates.builder()
                    .name("my-rs-v001")
                    .kind("replicaSet")
                    .namespace(NAMESAPCE)
                    .build()));
    Map<String, Object> context =
        getContext(
            DeployManifestContext.builder()
                .trafficManagement(
                    DeployManifestContext.TrafficManagement.builder()
                        .enabled(true)
                        .options(
                            DeployManifestContext.TrafficManagement.Options.builder()
                                .strategy(ManifestStrategyType.BLUE_GREEN)
                                .build())
                        .build())
                .build());
    StageExecutionImpl stage =
        new StageExecutionImpl(
            new PipelineExecutionImpl(ExecutionType.PIPELINE, APPLICATION),
            DeployManifestStage.PIPELINE_CONFIG_TYPE,
            context);
    assertThat(getAfterStages(stage))
        .extracting(StageExecution::getType)
        .containsExactly(DeleteManifestStage.PIPELINE_CONFIG_TYPE);
    assertThat(getAfterStages(stage))
        .extracting(s -> s.getContext().get("account"))
        .containsExactly(ACCOUNT);
    assertThat(getAfterStages(stage))
        .extracting(s -> s.getContext().get("app"))
        .containsExactly(APPLICATION);
    assertThat(getAfterStages(stage))
        .extracting(s -> s.getContext().get("location"))
        .containsExactly(NAMESAPCE);
    assertThat(getAfterStages(stage))
        .extracting(s -> s.getContext().get("manifestName"))
        .containsExactly("replicaSet my-rs-v000");
  }

  @Test
  void rolloutStrategyHighlander() {
    when(oortService.getClusterManifests(ACCOUNT, NAMESAPCE, "replicaSet", APPLICATION, CLUSTER))
        .thenReturn(
            ImmutableList.of(
                ManifestCoordinates.builder()
                    .name("my-rs-v000")
                    .kind("replicaSet")
                    .namespace(NAMESAPCE)
                    .build(),
                ManifestCoordinates.builder()
                    .name("my-rs-v001")
                    .kind("replicaSet")
                    .namespace(NAMESAPCE)
                    .build()));
    Map<String, Object> context =
        getContext(
            DeployManifestContext.builder()
                .trafficManagement(
                    DeployManifestContext.TrafficManagement.builder()
                        .enabled(true)
                        .options(
                            DeployManifestContext.TrafficManagement.Options.builder()
                                .strategy(ManifestStrategyType.HIGHLANDER)
                                .build())
                        .build())
                .build());
    StageExecutionImpl stage =
        new StageExecutionImpl(
            new PipelineExecutionImpl(ExecutionType.PIPELINE, APPLICATION),
            DeployManifestStage.PIPELINE_CONFIG_TYPE,
            context);
    assertThat(getAfterStages(stage))
        .extracting(StageExecution::getType)
        .containsExactly(
            DisableManifestStage.PIPELINE_CONFIG_TYPE, DeleteManifestStage.PIPELINE_CONFIG_TYPE);
    assertThat(getAfterStages(stage))
        .extracting(s -> s.getContext().get("account"))
        .containsExactly(ACCOUNT, ACCOUNT);
    assertThat(getAfterStages(stage))
        .extracting(s -> s.getContext().get("app"))
        .containsExactly(APPLICATION, APPLICATION);
    assertThat(getAfterStages(stage))
        .extracting(s -> s.getContext().get("location"))
        .containsExactly(NAMESAPCE, NAMESAPCE);
    assertThat(getAfterStages(stage))
        .extracting(s -> s.getContext().get("manifestName"))
        .containsExactly("replicaSet my-rs-v000", "replicaSet my-rs-v000");
  }

  @Test
  void rolloutStrategyNoClusterSiblings() {
    when(oortService.getClusterManifests(ACCOUNT, NAMESAPCE, "replicaSet", APPLICATION, CLUSTER))
        .thenReturn(
            ImmutableList.of(
                ManifestCoordinates.builder()
                    .name("my-rs-v001")
                    .kind("replicaSet")
                    .namespace(NAMESAPCE)
                    .build()));
    Map<String, Object> context =
        getContext(
            DeployManifestContext.builder()
                .trafficManagement(
                    DeployManifestContext.TrafficManagement.builder()
                        .enabled(true)
                        .options(
                            DeployManifestContext.TrafficManagement.Options.builder()
                                .strategy(ManifestStrategyType.RED_BLACK)
                                .build())
                        .build())
                .build());
    StageExecutionImpl stage =
        new StageExecutionImpl(
            new PipelineExecutionImpl(ExecutionType.PIPELINE, APPLICATION),
            DeployManifestStage.PIPELINE_CONFIG_TYPE,
            context);
    assertThat(getAfterStages(stage)).isEmpty();
  }

  @Test
  void rolloutStrategyBlueGreenNoClusterSiblings() {
    when(oortService.getClusterManifests(ACCOUNT, NAMESAPCE, "replicaSet", APPLICATION, CLUSTER))
        .thenReturn(
            ImmutableList.of(
                ManifestCoordinates.builder()
                    .name("my-rs-v001")
                    .kind("replicaSet")
                    .namespace(NAMESAPCE)
                    .build()));
    Map<String, Object> context =
        getContext(
            DeployManifestContext.builder()
                .trafficManagement(
                    DeployManifestContext.TrafficManagement.builder()
                        .enabled(true)
                        .options(
                            DeployManifestContext.TrafficManagement.Options.builder()
                                .strategy(ManifestStrategyType.BLUE_GREEN)
                                .build())
                        .build())
                .build());
    StageExecutionImpl stage =
        new StageExecutionImpl(
            new PipelineExecutionImpl(ExecutionType.PIPELINE, APPLICATION),
            DeployManifestStage.PIPELINE_CONFIG_TYPE,
            context);
    assertThat(getAfterStages(stage)).isEmpty();
  }

  private Iterable<StageExecution> getAfterStages(StageExecutionImpl stage) {
    StageGraphBuilderImpl graph = StageGraphBuilderImpl.afterStages(stage);
    deployManifestStage.afterStages(stage, graph);
    return graph.build();
  }

  private void givenManifestIsStable() {

    var manifest =
        Manifest.builder()
            .status(
                Manifest.Status.builder()
                    .stable(Manifest.Condition.emptyTrue())
                    .failed(Manifest.Condition.emptyFalse())
                    .build())
            .build();

    givenManifestIs(manifest);
  }

  private void givenManifestIsNotStable() {
    var manifest =
        Manifest.builder()
            .status(
                Manifest.Status.builder()
                    .stable(Manifest.Condition.emptyFalse())
                    .failed(Manifest.Condition.emptyFalse())
                    .build())
            .build();

    givenManifestIs(manifest);
  }

  private void givenManifestIs(Manifest manifest) {
    when(oortService.getManifest(anyString(), anyString(), anyBoolean())).thenReturn(manifest);
  }
}
