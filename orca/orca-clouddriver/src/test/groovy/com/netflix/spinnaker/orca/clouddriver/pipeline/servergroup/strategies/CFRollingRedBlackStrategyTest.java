/*
 * Copyright 2019 Pivotal, Inc.
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

package com.netflix.spinnaker.orca.clouddriver.pipeline.servergroup.strategies;

import static com.netflix.spinnaker.orca.api.pipeline.models.ExecutionType.PIPELINE;
import static java.util.Collections.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spinnaker.kork.artifacts.model.Artifact;
import com.netflix.spinnaker.kork.dynamicconfig.SpringDynamicConfigService;
import com.netflix.spinnaker.moniker.Moniker;
import com.netflix.spinnaker.orca.api.pipeline.models.StageExecution;
import com.netflix.spinnaker.orca.clouddriver.OortService;
import com.netflix.spinnaker.orca.clouddriver.pipeline.cluster.DisableClusterStage;
import com.netflix.spinnaker.orca.clouddriver.pipeline.cluster.ShrinkClusterStage;
import com.netflix.spinnaker.orca.clouddriver.pipeline.servergroup.CreateServerGroupStage;
import com.netflix.spinnaker.orca.clouddriver.pipeline.servergroup.ResizeServerGroupStage;
import com.netflix.spinnaker.orca.clouddriver.pipeline.servergroup.support.DetermineTargetServerGroupStage;
import com.netflix.spinnaker.orca.clouddriver.pipeline.servergroup.support.TargetServerGroup;
import com.netflix.spinnaker.orca.clouddriver.pipeline.servergroup.support.TargetServerGroupResolver;
import com.netflix.spinnaker.orca.front50.pipeline.PipelineStage;
import com.netflix.spinnaker.orca.kato.pipeline.support.ResizeStrategy;
import com.netflix.spinnaker.orca.kato.pipeline.support.ResizeStrategySupport;
import com.netflix.spinnaker.orca.pipeline.WaitStage;
import com.netflix.spinnaker.orca.pipeline.model.PipelineExecutionImpl;
import com.netflix.spinnaker.orca.pipeline.model.StageExecutionImpl;
import com.netflix.spinnaker.orca.pipeline.util.ArtifactUtils;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import okhttp3.MediaType;
import okhttp3.ResponseBody;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;
import org.springframework.core.env.Environment;
import org.springframework.mock.env.MockEnvironment;
import retrofit2.mock.Calls;

class CFRollingRedBlackStrategyTest {
  private CFRollingRedBlackStrategy strategy;
  private Environment env = new MockEnvironment();
  private SpringDynamicConfigService springDynamicConfigService = new SpringDynamicConfigService();
  private PipelineStage pipelineStage = mock(PipelineStage.class);
  private ResizeStrategySupport resizeStrategySupport = mock(ResizeStrategySupport.class);
  private ArtifactUtils artifactUtils = mock(ArtifactUtils.class);
  private OortService oortService = mock(OortService.class);
  private TargetServerGroupResolver targetServerGroupResolver =
      mock(TargetServerGroupResolver.class);
  private final ResizeStrategy.Capacity zeroCapacity = new ResizeStrategy.Capacity(0, 0, 0);
  private final ObjectMapper objectMapper = new ObjectMapper();

  {
    springDynamicConfigService.setEnvironment(env);
    strategy =
        new CFRollingRedBlackStrategy(
            null,
            artifactUtils,
            Optional.of(pipelineStage),
            resizeStrategySupport,
            targetServerGroupResolver,
            objectMapper,
            oortService);
  }

  @Test
  void composeFlowWithDelayBeforeScaleDown() throws IOException {
    List<Object> targetPercentageList = Stream.of(50, 100).collect(Collectors.toList());
    Map<String, Object> direct = new HashMap<>();
    direct.put("instances", 4);
    direct.put("memory", "64M");
    Map<String, Object> manifest = new HashMap<>();
    manifest.put("direct", direct);
    Map<String, Object> source = createSource();

    Map<String, Object> context = createBasicContext();
    context.put("manifest", manifest);
    context.put("targetPercentages", targetPercentageList);
    context.put("source", source);
    context.put("delayBeforeScaleDownSec", 5L);
    StageExecution deployServerGroupStage =
        new StageExecutionImpl(
            new PipelineExecutionImpl(PIPELINE, "unit"),
            CreateServerGroupStage.PIPELINE_CONFIG_TYPE,
            context);
    when(targetServerGroupResolver.resolve(any()))
        .thenReturn(singletonList(new TargetServerGroup(Collections.emptyMap())));
    when(resizeStrategySupport.getCapacity(any(), any(), any(), any()))
        .thenReturn(new ResizeStrategy.Capacity(1, 1, 1));

    List<StageExecution> stages = strategy.composeFlow(deployServerGroupStage);

    assertThat(stages.stream().map(StageExecution::getType))
        .containsExactly(
            DetermineTargetServerGroupStage.PIPELINE_CONFIG_TYPE,
            ResizeServerGroupStage.TYPE,
            ResizeServerGroupStage.TYPE,
            ResizeServerGroupStage.TYPE,
            ResizeServerGroupStage.TYPE,
            ShrinkClusterStage.STAGE_TYPE,
            DisableClusterStage.STAGE_TYPE,
            ResizeServerGroupStage.TYPE,
            WaitStage.STAGE_TYPE);
    assertThat(
            stages.stream()
                .filter(stage -> stage.getType().equals(WaitStage.STAGE_TYPE))
                .map(stage -> stage.getContext().get("waitTime")))
        .containsExactly(5L);
  }

  @Test
  void composeFlowWithDelayBeforeCleanup() {
    List<Object> targetPercentageList = Stream.of(50, 100).collect(Collectors.toList());
    Map<String, Object> direct = new HashMap<>();
    direct.put("instances", 4);
    direct.put("memory", "64M");
    Map<String, Object> manifest = new HashMap<>();
    manifest.put("direct", direct);
    Map<String, Object> source = createSource();

    Map<String, Object> context = createBasicContext();
    context.put("manifest", manifest);
    context.put("targetPercentages", targetPercentageList);
    context.put("source", source);
    context.put("delayBeforeCleanup", 5L);
    StageExecution deployServerGroupStage =
        new StageExecutionImpl(
            new PipelineExecutionImpl(PIPELINE, "unit"),
            CreateServerGroupStage.PIPELINE_CONFIG_TYPE,
            context);

    when(targetServerGroupResolver.resolve(any()))
        .thenReturn(singletonList(new TargetServerGroup(Collections.emptyMap())));
    when(resizeStrategySupport.getCapacity(any(), any(), any(), any()))
        .thenReturn(new ResizeStrategy.Capacity(1, 1, 1));

    List<StageExecution> stages = strategy.composeFlow(deployServerGroupStage);

    assertThat(stages.stream().map(StageExecution::getType))
        .containsExactly(
            DetermineTargetServerGroupStage.PIPELINE_CONFIG_TYPE,
            ResizeServerGroupStage.TYPE,
            WaitStage.STAGE_TYPE,
            ResizeServerGroupStage.TYPE,
            ResizeServerGroupStage.TYPE,
            WaitStage.STAGE_TYPE,
            ResizeServerGroupStage.TYPE,
            ShrinkClusterStage.STAGE_TYPE,
            DisableClusterStage.STAGE_TYPE,
            ResizeServerGroupStage.TYPE);
    assertThat(
            stages.stream()
                .filter(stage -> stage.getType().equals(WaitStage.STAGE_TYPE))
                .map(stage -> stage.getContext().get("waitTime")))
        .containsExactly(5L, 5L);
  }

  @Test
  void composeFlowWithNoSourceAndManifestDirect() {
    List<Object> targetPercentageList = Stream.of(50, 75, 100).collect(Collectors.toList());
    Map<String, Object> direct = new HashMap<>();
    direct.put("instances", 4);
    direct.put("memory", "64M");
    Map<String, Object> manifest = new HashMap<>();
    manifest.put("direct", direct);

    Map<String, Object> context = createBasicContext();
    context.put("manifest", manifest);
    context.put("targetPercentages", targetPercentageList);

    Map<String, Object> expectedDirect = new HashMap<>();
    expectedDirect.put("memory", "64M");
    expectedDirect.put("instances", 1);
    Map expectedManifest = Collections.singletonMap("direct", expectedDirect);
    ResizeStrategy.Capacity resizeTo4Capacity = new ResizeStrategy.Capacity(4, 4, 4);
    StageExecution deployServerGroupStage =
        new StageExecutionImpl(
            new PipelineExecutionImpl(PIPELINE, "unit"),
            CreateServerGroupStage.PIPELINE_CONFIG_TYPE,
            context);

    List<StageExecution> stages = strategy.composeFlow(deployServerGroupStage);

    assertThat(stages.stream().map(stage -> stage.getContext().get("capacity")))
        .containsExactly(null, resizeTo4Capacity, resizeTo4Capacity, resizeTo4Capacity);
    assertThat(stages.stream().map(stage -> stage.getContext().get("scalePct")))
        .containsExactly(null, 50, 75, 100);
    assertThat(stages.stream().map(StageExecution::getType))
        .containsExactly(
            DetermineTargetServerGroupStage.PIPELINE_CONFIG_TYPE,
            ResizeServerGroupStage.TYPE,
            ResizeServerGroupStage.TYPE,
            ResizeServerGroupStage.TYPE);
    assertThat(deployServerGroupStage.getContext().get("targetSize")).isNull();
    assertThat(deployServerGroupStage.getContext().get("useSourceCapacity")).isNull();
    assertThat(deployServerGroupStage.getContext().get("capacity")).isEqualTo(zeroCapacity);
    assertThat(deployServerGroupStage.getContext().get("manifest")).isEqualTo(expectedManifest);
    verifyNoMoreInteractions(artifactUtils);
    verifyNoMoreInteractions(oortService);
  }

  @Test
  void composeFlowWithSourceAndManifestDirect() {
    List<Object> targetPercentageList = Stream.of(50, 75, 100).collect(Collectors.toList());
    Map<String, Object> context = createBasicContext();
    Map<String, Object> direct = new HashMap<>();
    direct.put("instances", 4);
    direct.put("memory", "64M");
    Map<String, Object> manifest = new HashMap<>();
    manifest.put("direct", direct);
    context.put("manifest", manifest);
    context.put("targetPercentages", targetPercentageList);
    context.put("source", createSource());
    StageExecution deployServerGroupStage =
        new StageExecutionImpl(
            new PipelineExecutionImpl(PIPELINE, "unit"),
            CreateServerGroupStage.PIPELINE_CONFIG_TYPE,
            context);
    ResizeStrategy.Capacity initialSourceCapacity = new ResizeStrategy.Capacity(8, 8, 8);
    ResizeStrategy.Capacity resetOriginalCapacity =
        new ResizeStrategy.Capacity(initialSourceCapacity.getMax(), 0, 0);

    when(targetServerGroupResolver.resolve(any()))
        .thenReturn(singletonList(new TargetServerGroup(Collections.emptyMap())));
    when(resizeStrategySupport.getCapacity(any(), any(), any(), any()))
        .thenReturn(initialSourceCapacity);

    Map<String, Object> expectedDirect = new HashMap<>();
    expectedDirect.put("memory", "64M");
    expectedDirect.put("instances", 1);
    Map expectedManifest = Collections.singletonMap("direct", expectedDirect);

    List<StageExecution> stages = strategy.composeFlow(deployServerGroupStage);

    ResizeStrategy.Capacity resizeTo4Capacity = new ResizeStrategy.Capacity(4, 4, 4);
    assertThat(stages.stream().map(StageExecution::getType))
        .containsExactly(
            DetermineTargetServerGroupStage.PIPELINE_CONFIG_TYPE,
            ResizeServerGroupStage.TYPE,
            ResizeServerGroupStage.TYPE,
            ResizeServerGroupStage.TYPE,
            ResizeServerGroupStage.TYPE,
            ResizeServerGroupStage.TYPE,
            ResizeServerGroupStage.TYPE,
            ShrinkClusterStage.STAGE_TYPE,
            DisableClusterStage.STAGE_TYPE,
            ResizeServerGroupStage.TYPE);
    assertThat(stages.stream().map(stage -> stage.getContext().get("scalePct")))
        .containsExactly(null, 50, 50, 75, 25, 100, 0, null, null, 100);
    assertThat(stages.stream().map(stage -> stage.getContext().get("capacity")))
        .containsExactly(
            null,
            resizeTo4Capacity,
            initialSourceCapacity,
            resizeTo4Capacity,
            initialSourceCapacity,
            resizeTo4Capacity,
            initialSourceCapacity,
            null,
            null,
            resetOriginalCapacity);
    assertThat(deployServerGroupStage.getContext().get("targetSize")).isNull();
    assertThat(deployServerGroupStage.getContext().get("useSourceCapacity")).isNull();
    assertThat(deployServerGroupStage.getContext().get("capacity")).isEqualTo(zeroCapacity);
    assertThat(deployServerGroupStage.getContext().get("manifest")).isEqualTo(expectedManifest);
    verifyNoMoreInteractions(artifactUtils);
    verifyNoMoreInteractions(oortService);
  }

  @Test
  void composeFlowWithNoSourceAndManifestArtifactConvertsManifestToDirect() throws IOException {
    String artifactId = "artifact-id";
    Map<String, Object> manifest = new HashMap<>();
    manifest.put("artifactId", artifactId);
    manifest.put("artifact", new HashMap<>());
    Map<String, Object> context = createBasicContext();
    List<Object> targetPercentageList = Stream.of(50, 75, 100).collect(Collectors.toList());
    context.put("manifest", manifest);
    context.put("targetPercentages", targetPercentageList);
    Artifact boundArtifactForStage = Artifact.builder().build();
    Map<String, Object> application = new HashMap<>();
    application.put("instances", "4");
    application.put("memory", "64M");
    application.put("diskQuota", "128M");
    Map<String, Object> body = singletonMap("applications", singletonList(application));
    ResponseBody oortServiceResponse = createMockOortServiceResponse(body);
    StageExecution deployServerGroupStage =
        new StageExecutionImpl(
            new PipelineExecutionImpl(PIPELINE, "unit"),
            CreateServerGroupStage.PIPELINE_CONFIG_TYPE,
            context);
    ResizeStrategy.Capacity resizeTo4Capacity = new ResizeStrategy.Capacity(4, 4, 4);

    when(artifactUtils.getBoundArtifactForStage(any(), any(), any()))
        .thenReturn(boundArtifactForStage);
    when(oortService.fetchArtifact(any())).thenReturn(Calls.response(oortServiceResponse));

    Map<String, Object> expectedDirect = new HashMap<>();
    expectedDirect.put("memory", "64M");
    expectedDirect.put("diskQuota", "128M");
    expectedDirect.put("instances", 1);
    Map expectedManifest = Collections.singletonMap("direct", expectedDirect);

    List<StageExecution> stages = strategy.composeFlow(deployServerGroupStage);

    assertThat(stages.stream().map(stage -> stage.getContext().get("capacity")))
        .containsExactly(null, resizeTo4Capacity, resizeTo4Capacity, resizeTo4Capacity);
    assertThat(stages.stream().map(stage -> stage.getContext().get("scalePct")))
        .containsExactly(null, 50, 75, 100);
    assertThat(stages.stream().map(StageExecution::getType))
        .containsExactly(
            DetermineTargetServerGroupStage.PIPELINE_CONFIG_TYPE,
            ResizeServerGroupStage.TYPE,
            ResizeServerGroupStage.TYPE,
            ResizeServerGroupStage.TYPE);
    assertThat(deployServerGroupStage.getContext().get("targetSize")).isNull();
    assertThat(deployServerGroupStage.getContext().get("useSourceCapacity")).isNull();
    assertThat(deployServerGroupStage.getContext().get("capacity")).isEqualTo(zeroCapacity);
    assertThat(deployServerGroupStage.getContext().get("manifest")).isEqualTo(expectedManifest);
    verify(artifactUtils)
        .getBoundArtifactForStage(deployServerGroupStage, artifactId, Artifact.builder().build());
    verify(oortService).fetchArtifact(boundArtifactForStage);
  }

  @Test
  void composeFlowWithSourceAndManifestArtifactConvertsManifestToDirect() throws IOException {
    String artifactId = "artifact-id";
    Map<String, Object> artifact = new HashMap<>();
    Map<String, Object> manifest = new HashMap<>();
    manifest.put("artifactId", artifactId);
    manifest.put("artifact", artifact);
    Map<String, Object> expectedDirect = new HashMap<>();
    expectedDirect.put("memory", "64M");
    expectedDirect.put("diskQuota", "128M");
    expectedDirect.put("instances", 1);
    Map expectedManifest = Collections.singletonMap("direct", expectedDirect);
    List<Object> targetPercentageList = Stream.of(50, 75, 100).collect(Collectors.toList());
    Map<String, Object> context = createBasicContext();
    context.put("manifest", manifest);
    context.put("targetPercentages", targetPercentageList);
    context.put("source", createSource());
    ResizeStrategy.Capacity resizeTo4Capacity = new ResizeStrategy.Capacity(4, 4, 4);
    ResizeStrategy.Capacity initialSourceCapacity = new ResizeStrategy.Capacity(8, 8, 8);
    ResizeStrategy.Capacity resetOriginalCapacity =
        new ResizeStrategy.Capacity(initialSourceCapacity.getMax(), 0, 0);

    StageExecution deployServerGroupStage =
        new StageExecutionImpl(new PipelineExecutionImpl(PIPELINE, "unit"), "type", context);
    Artifact boundArtifactForStage = Artifact.builder().build();
    Map<String, Object> application = new HashMap<>();
    application.put("instances", "4");
    application.put("memory", "64M");
    application.put("diskQuota", "128M");
    Map<String, Object> body = singletonMap("applications", singletonList(application));
    ResponseBody oortServiceResponse = createMockOortServiceResponse(body);

    when(targetServerGroupResolver.resolve(any()))
        .thenReturn(singletonList(new TargetServerGroup(Collections.emptyMap())));
    when(resizeStrategySupport.getCapacity(any(), any(), any(), any()))
        .thenReturn(initialSourceCapacity);
    when(artifactUtils.getBoundArtifactForStage(any(), any(), any()))
        .thenReturn(boundArtifactForStage);
    when(oortService.fetchArtifact(any())).thenReturn(Calls.response(oortServiceResponse));

    List<StageExecution> stages = strategy.composeFlow(deployServerGroupStage);

    assertThat(stages.stream().map(StageExecution::getType))
        .containsExactly(
            DetermineTargetServerGroupStage.PIPELINE_CONFIG_TYPE,
            ResizeServerGroupStage.TYPE,
            ResizeServerGroupStage.TYPE,
            ResizeServerGroupStage.TYPE,
            ResizeServerGroupStage.TYPE,
            ResizeServerGroupStage.TYPE,
            ResizeServerGroupStage.TYPE,
            ShrinkClusterStage.STAGE_TYPE,
            DisableClusterStage.STAGE_TYPE,
            ResizeServerGroupStage.TYPE);

    assertThat(stages.stream().map(stage -> stage.getContext().get("capacity")))
        .containsExactly(
            null,
            resizeTo4Capacity,
            initialSourceCapacity,
            resizeTo4Capacity,
            initialSourceCapacity,
            resizeTo4Capacity,
            initialSourceCapacity,
            null,
            null,
            resetOriginalCapacity);
    assertThat(stages.stream().map(stage -> stage.getContext().get("scalePct")))
        .containsExactly(null, 50, 50, 75, 25, 100, 0, null, null, 100);
    assertThat(deployServerGroupStage.getContext().get("targetSize")).isNull();
    assertThat(deployServerGroupStage.getContext().get("useSourceCapacity")).isNull();
    assertThat(deployServerGroupStage.getContext().get("capacity")).isEqualTo(zeroCapacity);
    assertThat(deployServerGroupStage.getContext().get("manifest")).isEqualTo(expectedManifest);
    verify(artifactUtils)
        .getBoundArtifactForStage(deployServerGroupStage, artifactId, Artifact.builder().build());
    verify(oortService).fetchArtifact(boundArtifactForStage);
  }

  @NotNull
  private ResponseBody createMockOortServiceResponse(Object body) throws IOException {
    return ResponseBody.create(
        objectMapper.writeValueAsBytes(body), MediaType.parse("application/json"));
  }

  @NotNull
  private Map<String, Object> createSource() {
    Map<String, Object> source = new HashMap<>();
    source.put("account", "account");
    source.put("region", "org > space");
    source.put("asgName", "asg-name");
    source.put("serverGroupName", "server-group-name");
    return source;
  }

  private Map<String, Object> createBasicContext() {
    Moniker moniker = new Moniker("unit", null, null, "test0", null);
    Map<String, Object> rollbackOnFailure = Collections.singletonMap("onFailure", true);

    Map<String, Object> context = new HashMap<>();
    context.put("account", "testAccount");
    context.put("application", "unit");
    context.put("cloudProvider", "cloudfoundry");
    context.put("freeFormDetails", "detail");
    context.put("maxRemainingAsgs", 2);
    context.put("moniker", moniker);
    context.put("name", "Deploy in test > test");
    context.put("provider", "cloudfoundry");
    context.put("region", "test > test");
    context.put("rollback", rollbackOnFailure);
    context.put("scaleDown", "false");
    context.put("stack", "test0");
    context.put("startApplication", "true");
    context.put("strategy", "cfrollingredblack");
    context.put("type", "createServerGroup");
    return context;
  }
}
