/*
 * Copyright 2025 Salesforce, Inc.
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

package com.netflix.spinnaker.orca.front50;

import static com.netflix.spinnaker.orca.api.pipeline.models.ExecutionType.PIPELINE;
import static com.netflix.spinnaker.orca.test.model.ExecutionBuilder.pipeline;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spectator.api.NoopRegistry;
import com.netflix.spinnaker.kork.artifacts.model.Artifact;
import com.netflix.spinnaker.kork.artifacts.model.ExpectedArtifact;
import com.netflix.spinnaker.kork.web.exceptions.InvalidRequestException;
import com.netflix.spinnaker.orca.api.pipeline.models.PipelineExecution;
import com.netflix.spinnaker.orca.api.pipeline.models.StageExecution;
import com.netflix.spinnaker.orca.api.pipeline.models.Trigger;
import com.netflix.spinnaker.orca.jackson.OrcaObjectMapper;
import com.netflix.spinnaker.orca.pipeline.ExecutionLauncher;
import com.netflix.spinnaker.orca.pipeline.model.DefaultTrigger;
import com.netflix.spinnaker.orca.pipeline.model.PipelineExecutionImpl;
import com.netflix.spinnaker.orca.pipeline.model.StageExecutionImpl;
import com.netflix.spinnaker.orca.pipeline.persistence.ExecutionRepository;
import com.netflix.spinnaker.orca.pipeline.util.ArtifactUtils;
import com.netflix.spinnaker.orca.pipeline.util.ContextParameterProcessor;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.context.support.StaticApplicationContext;

class DependentPipelineStarterTest {

  private DependentPipelineStarter dependentPipelineStarter;
  private ObjectMapper mapper;
  private ExecutionRepository executionRepository = mock(ExecutionRepository.class);
  private ArtifactUtils artifactUtils;

  @BeforeEach
  void setup() {
    mapper = OrcaObjectMapper.newInstance();
    executionRepository = mock(ExecutionRepository.class);
    artifactUtils =
        spy(new ArtifactUtils(mapper, executionRepository, new ContextParameterProcessor()));
  }

  @Test
  void shouldOnlyPropagateCredentialsWhenExplicitlyProvided() {
    // given
    Map<String, Object> triggeredPipelineConfig = new HashMap<>();
    triggeredPipelineConfig.put("name", "triggered");
    triggeredPipelineConfig.put("id", "triggered");

    PipelineExecution parentPipeline = pipeline();
    parentPipeline.setName("parent");
    parentPipeline.setRootId("parent-root-id");
    parentPipeline.setAuthentication(
        new PipelineExecution.AuthenticationDetails("parentUser", "acct1", "acct2"));

    Map<String, String> gotMDC = new HashMap<>();
    ExecutionLauncher executionLauncher = mock(ExecutionLauncher.class);
    when(executionLauncher.start(eq(PIPELINE), any(), any()))
        .thenAnswer(
            invocation -> {
              Map<String, Object> p = invocation.getArgument(1);
              gotMDC.put("X-SPINNAKER-USER", MDC.get("X-SPINNAKER-USER"));
              gotMDC.put("X-SPINNAKER-ACCOUNTS", MDC.get("X-SPINNAKER-ACCOUNTS"));

              PipelineExecution result = pipeline();
              result.setName((String) p.get("name"));
              result.setId((String) p.get("id"));
              // Since we're mocking ExecutionLauncher, it's a little artificial
              // to populate rootId here only to assert on it below, so don't.
              // What's important is that we verify that executionLauncher.start
              // was called with the expected rootID argument, and that does
              // happen below.
              result.setTrigger(mapper.convertValue(p.get("trigger"), Trigger.class));
              return result;
            });

    StaticApplicationContext applicationContext = new StaticApplicationContext();
    applicationContext.getBeanFactory().registerSingleton("pipelineLauncher", executionLauncher);

    dependentPipelineStarter =
        new DependentPipelineStarter(
            applicationContext,
            mapper,
            new ContextParameterProcessor(),
            Optional.empty(),
            Optional.of(artifactUtils),
            new NoopRegistry());

    // Test with authenticated user
    PipelineExecution result =
        dependentPipelineStarter.trigger(
            triggeredPipelineConfig,
            null,
            parentPipeline,
            new HashMap<>(),
            null,
            buildAuthenticatedUser("user", List.of("acct3", "acct4")));
    MDC.clear();

    verify(executionLauncher, never()).start(eq(PIPELINE), any());
    verify(executionLauncher).start(eq(PIPELINE), any(), eq(parentPipeline.getRootId()));

    assertThat(result.getName()).isEqualTo("triggered");
    assertThat(gotMDC.get("X-SPINNAKER-USER")).isEqualTo("user");
    assertThat(gotMDC.get("X-SPINNAKER-ACCOUNTS")).isEqualTo("acct3,acct4");

    // Test without authenticated user
    result =
        dependentPipelineStarter.trigger(
            triggeredPipelineConfig, null, parentPipeline, new HashMap<>(), null, null);
    MDC.clear();

    verify(executionLauncher, never()).start(eq(PIPELINE), any());
    verify(executionLauncher, times(2)).start(eq(PIPELINE), any(), eq(parentPipeline.getRootId()));

    assertThat(result.getName()).isEqualTo("triggered");
    assertThat(gotMDC.get("X-SPINNAKER-USER")).isNull();
    assertThat(gotMDC.get("X-SPINNAKER-ACCOUNTS")).isNull();
  }

  @Test
  void shouldPropagateDryRunFlag() {
    // given
    Map<String, Object> triggeredPipelineConfig = new HashMap<>();
    triggeredPipelineConfig.put("name", "triggered");
    triggeredPipelineConfig.put("id", "triggered");

    PipelineExecution parentPipeline = pipeline();
    parentPipeline.setName("parent");
    parentPipeline.setRootId("parent-root-id");
    parentPipeline.setTrigger(
        new DefaultTrigger(
            "manual",
            null,
            "fzlem@netflix.com",
            new HashMap<>(),
            new ArrayList<>(),
            new ArrayList<>(),
            false,
            true));
    parentPipeline.setAuthentication(
        new PipelineExecution.AuthenticationDetails("parentUser", "acct1", "acct2"));

    ExecutionLauncher executionLauncher = mock(ExecutionLauncher.class);
    when(executionLauncher.start(eq(PIPELINE), any(), any()))
        .thenAnswer(
            invocation -> {
              Map<String, Object> p = invocation.getArgument(1);
              PipelineExecution result = pipeline();
              result.setName((String) p.get("name"));
              result.setId((String) p.get("id"));
              result.setTrigger(mapper.convertValue(p.get("trigger"), Trigger.class));
              return result;
            });

    StaticApplicationContext applicationContext = new StaticApplicationContext();
    applicationContext.getBeanFactory().registerSingleton("pipelineLauncher", executionLauncher);

    dependentPipelineStarter =
        new DependentPipelineStarter(
            applicationContext,
            mapper,
            new ContextParameterProcessor(),
            Optional.empty(),
            Optional.of(artifactUtils),
            new NoopRegistry());

    // when
    PipelineExecution result =
        dependentPipelineStarter.trigger(
            triggeredPipelineConfig,
            null,
            parentPipeline,
            new HashMap<>(),
            null,
            buildAuthenticatedUser("user", new ArrayList<>()));

    // then
    verify(executionLauncher, never()).start(eq(PIPELINE), any());
    verify(executionLauncher).start(eq(PIPELINE), any(), eq(parentPipeline.getRootId()));

    assertThat(result.getTrigger().isDryRun()).isTrue();
  }

  @Test
  void shouldFindArtifactsFromTriggeringPipeline() {
    // given
    Map<String, Object> expectedArtifact = new HashMap<>();
    expectedArtifact.put("id", "id1");
    Map<String, Object> matchArtifact = new HashMap<>();
    matchArtifact.put("kind", "gcs");
    matchArtifact.put("name", "gs://test/file.yaml");
    matchArtifact.put("type", "gcs/object");
    expectedArtifact.put("matchArtifact", matchArtifact);

    Map<String, Object> triggeredPipelineConfig = new HashMap<>();
    triggeredPipelineConfig.put("name", "triggered");
    triggeredPipelineConfig.put("id", "triggered");
    triggeredPipelineConfig.put("expectedArtifacts", List.of(expectedArtifact));

    Artifact testArtifact =
        Artifact.builder().type("gcs/object").name("gs://test/file.yaml").build();

    PipelineExecution parentPipeline = pipeline();
    parentPipeline.setName("parent");
    parentPipeline.setRootId("parent-root-id");
    parentPipeline.setTrigger(
        new DefaultTrigger("webhook", null, "test", new HashMap<>(), List.of(testArtifact)));
    parentPipeline.setAuthentication(
        new PipelineExecution.AuthenticationDetails("parentUser", "acct1", "acct2"));

    ExecutionLauncher executionLauncher = mock(ExecutionLauncher.class);
    when(executionLauncher.start(eq(PIPELINE), any(), any()))
        .thenAnswer(
            invocation -> {
              Map<String, Object> p = invocation.getArgument(1);
              PipelineExecution result = pipeline();
              result.setName((String) p.get("name"));
              result.setId((String) p.get("id"));
              result.setTrigger(mapper.convertValue(p.get("trigger"), Trigger.class));
              return result;
            });

    StaticApplicationContext applicationContext = new StaticApplicationContext();
    applicationContext.getBeanFactory().registerSingleton("pipelineLauncher", executionLauncher);

    dependentPipelineStarter =
        new DependentPipelineStarter(
            applicationContext,
            mapper,
            new ContextParameterProcessor(),
            Optional.empty(),
            Optional.of(artifactUtils),
            new NoopRegistry());

    // when
    PipelineExecution result =
        dependentPipelineStarter.trigger(
            triggeredPipelineConfig,
            null,
            parentPipeline,
            new HashMap<>(),
            null,
            buildAuthenticatedUser("user", new ArrayList<>()));

    // then
    verify(executionLauncher, never()).start(eq(PIPELINE), any());
    verify(executionLauncher).start(eq(PIPELINE), any(), eq(parentPipeline.getRootId()));

    assertThat(result.getTrigger().getArtifacts()).hasSize(1);
    assertThat(result.getTrigger().getArtifacts().get(0).getName())
        .isEqualTo("gs://test/file.yaml");
  }

  @Test
  void shouldFindArtifactsFromParentPipelineStage() {
    // given
    Map<String, Object> triggeredPipelineConfig = new HashMap<>();
    triggeredPipelineConfig.put("name", "triggered");
    triggeredPipelineConfig.put("id", "triggered");

    Map<String, Object> matchArtifact = new HashMap<>();
    matchArtifact.put("kind", "gcs");
    matchArtifact.put("name", "gs://test/file.yaml");
    matchArtifact.put("type", "gcs/object");

    Map<String, Object> expectedArtifact = new HashMap<>();
    expectedArtifact.put("id", "id1");
    expectedArtifact.put("matchArtifact", matchArtifact);

    triggeredPipelineConfig.put("expectedArtifacts", Collections.singletonList(expectedArtifact));

    Artifact testArtifact =
        Artifact.builder().type("gcs/object").name("gs://test/file.yaml").build();

    PipelineExecution parentPipeline = createParentPipeline(testArtifact);
    ExecutionLauncher executionLauncher = mock(ExecutionLauncher.class);

    StaticApplicationContext applicationContext = new StaticApplicationContext();
    applicationContext.getBeanFactory().registerSingleton("pipelineLauncher", executionLauncher);

    dependentPipelineStarter =
        new DependentPipelineStarter(
            applicationContext,
            mapper,
            new ContextParameterProcessor(),
            Optional.empty(),
            Optional.of(artifactUtils),
            new NoopRegistry());

    when(executionLauncher.start(any(), any(), any()))
        .thenAnswer(
            invocation -> {
              Map<String, Object> config = invocation.getArgument(1);
              return createPipelineExecution(config);
            });

    // when
    PipelineExecution result =
        dependentPipelineStarter.trigger(
            triggeredPipelineConfig,
            null,
            parentPipeline,
            new HashMap<>(),
            "stage1",
            buildAuthenticatedUser("user", Collections.emptyList()));

    // then
    verify(executionLauncher, never()).start(eq(PIPELINE), any());
    verify(executionLauncher).start(eq(PIPELINE), any(), eq(parentPipeline.getRootId()));

    assertThat(result.getTrigger().getArtifacts()).hasSize(1);
    assertThat(result.getTrigger().getArtifacts().get(0).getName())
        .isEqualTo("gs://test/file.yaml");
  }

  @Test
  void shouldFindArtifactsFromTriggeringPipelineWithoutExpectedArtifacts() {
    // given
    Map<String, Object> expectedArtifact = new HashMap<>();
    expectedArtifact.put("id", "id1");
    Map<String, Object> matchArtifact = new HashMap<>();
    matchArtifact.put("kind", "gcs");
    matchArtifact.put("name", "gs://test/file.yaml");
    matchArtifact.put("type", "gcs/object");
    expectedArtifact.put("matchArtifact", matchArtifact);

    Map<String, Object> triggeredPipelineConfig = new HashMap<>();
    triggeredPipelineConfig.put("name", "triggered");
    triggeredPipelineConfig.put("id", "triggered");
    triggeredPipelineConfig.put("expectedArtifacts", List.of(expectedArtifact));

    Artifact testArtifact1 =
        Artifact.builder().type("gcs/object").name("gs://test/file.yaml").build();
    Artifact testArtifact2 =
        Artifact.builder().type("docker/image").name("gcr.io/project/image").build();

    PipelineExecution parentPipeline = pipeline();
    parentPipeline.setName("parent");
    parentPipeline.setRootId("parent-root-id");
    parentPipeline.setTrigger(
        new DefaultTrigger(
            "webhook", null, "test", new HashMap<>(), List.of(testArtifact1, testArtifact2)));
    parentPipeline.setAuthentication(
        new PipelineExecution.AuthenticationDetails("parentUser", "acct1", "acct2"));

    ExecutionLauncher executionLauncher = mock(ExecutionLauncher.class);
    StaticApplicationContext applicationContext = new StaticApplicationContext();
    applicationContext.getBeanFactory().registerSingleton("pipelineLauncher", executionLauncher);
    dependentPipelineStarter =
        new DependentPipelineStarter(
            applicationContext,
            mapper,
            new ContextParameterProcessor(),
            Optional.empty(),
            Optional.of(artifactUtils),
            new NoopRegistry());

    when(executionLauncher.start(eq(PIPELINE), any(), any()))
        .thenAnswer(
            invocation -> {
              Map<String, Object> p = invocation.getArgument(1);
              PipelineExecution result = pipeline();
              result.setName((String) p.get("name"));
              result.setId((String) p.get("id"));
              result.setTrigger(mapper.convertValue(p.get("trigger"), Trigger.class));
              return result;
            });

    // when
    PipelineExecution result =
        dependentPipelineStarter.trigger(
            triggeredPipelineConfig,
            null,
            parentPipeline,
            new HashMap<>(),
            null,
            buildAuthenticatedUser("user", new ArrayList<>()));

    // then
    verify(executionLauncher, never()).start(eq(PIPELINE), any());
    verify(executionLauncher).start(eq(PIPELINE), any(), eq(parentPipeline.getRootId()));

    assertThat(result.getTrigger().getArtifacts()).hasSize(2);
    assertThat(result.getTrigger().getArtifacts())
        .filteredOn(artifact -> artifact.getName().equals(testArtifact1.getName()))
        .hasSize(1);
    assertThat(result.getTrigger().getArtifacts())
        .filteredOn(artifact -> artifact.getName().equals(testArtifact2.getName()))
        .hasSize(1);
    assertThat(result.getTrigger().getResolvedExpectedArtifacts()).hasSize(1);
    assertThat(result.getTrigger().getResolvedExpectedArtifacts().get(0).getId()).isEqualTo("id1");
  }

  @Test
  void shouldFindExpectedArtifactsFromPipelineTrigger() {
    // given
    Map<String, Object> expectedArtifact = new HashMap<>();
    expectedArtifact.put("id", "id1");
    Map<String, Object> matchArtifact = new HashMap<>();
    matchArtifact.put("kind", "gcs");
    matchArtifact.put("name", "gs://test/file.yaml");
    matchArtifact.put("type", "gcs/object");
    expectedArtifact.put("matchArtifact", matchArtifact);

    Map<String, Object> pipelineTrigger = new HashMap<>();
    pipelineTrigger.put("type", "pipeline");
    pipelineTrigger.put("pipeline", "5e96d1e8-a3c0-4458-b3a4-fda17e0d5ab5");
    pipelineTrigger.put("expectedArtifactIds", List.of("id1"));

    Map<String, Object> jenkinsTrigger = new HashMap<>();
    jenkinsTrigger.put("type", "jenkins");

    Map<String, Object> triggeredPipelineConfig = new HashMap<>();
    triggeredPipelineConfig.put("name", "triggered");
    triggeredPipelineConfig.put("id", "triggered");
    triggeredPipelineConfig.put("expectedArtifacts", List.of(expectedArtifact));
    triggeredPipelineConfig.put("triggers", List.of(pipelineTrigger, jenkinsTrigger));

    Artifact testArtifact1 =
        Artifact.builder().type("gcs/object").name("gs://test/file.yaml").build();
    Artifact testArtifact2 =
        Artifact.builder().type("docker/image").name("gcr.io/project/image").build();

    PipelineExecution parentPipeline = pipeline();
    parentPipeline.setName("parent");
    parentPipeline.setRootId("parent-root-id");
    parentPipeline.setTrigger(
        new DefaultTrigger(
            "webhook", null, "test", new HashMap<>(), List.of(testArtifact1, testArtifact2)));
    parentPipeline.setAuthentication(
        new PipelineExecution.AuthenticationDetails("parentUser", "acct1", "acct2"));
    parentPipeline.setPipelineConfigId("5e96d1e8-a3c0-4458-b3a4-fda17e0d5ab5");

    ExecutionLauncher executionLauncher = mock(ExecutionLauncher.class);
    StaticApplicationContext applicationContext = new StaticApplicationContext();
    applicationContext.getBeanFactory().registerSingleton("pipelineLauncher", executionLauncher);
    dependentPipelineStarter =
        new DependentPipelineStarter(
            applicationContext,
            mapper,
            new ContextParameterProcessor(),
            Optional.empty(),
            Optional.of(artifactUtils),
            new NoopRegistry());

    when(executionLauncher.start(eq(PIPELINE), any(), any()))
        .thenAnswer(
            invocation -> {
              Map<String, Object> p = invocation.getArgument(1);
              PipelineExecution result = pipeline();
              result.setName((String) p.get("name"));
              result.setId((String) p.get("id"));
              result.setTrigger(mapper.convertValue(p.get("trigger"), Trigger.class));
              return result;
            });

    // when
    PipelineExecution result =
        dependentPipelineStarter.trigger(
            triggeredPipelineConfig,
            null,
            parentPipeline,
            new HashMap<>(),
            null,
            buildAuthenticatedUser("user", new ArrayList<>()));

    // then
    verify(executionLauncher, never()).start(eq(PIPELINE), any());
    verify(executionLauncher).start(eq(PIPELINE), any(), eq(parentPipeline.getRootId()));

    assertThat(result.getTrigger().getArtifacts()).hasSize(2);
    assertThat(result.getTrigger().getArtifacts())
        .filteredOn(artifact -> artifact.getName().equals(testArtifact1.getName()))
        .hasSize(1);
    assertThat(result.getTrigger().getArtifacts())
        .filteredOn(artifact -> artifact.getName().equals(testArtifact2.getName()))
        .hasSize(1);
    assertThat(result.getTrigger().getResolvedExpectedArtifacts()).hasSize(1);
    assertThat(
            result.getTrigger().getResolvedExpectedArtifacts().get(0).getBoundArtifact().getName())
        .isEqualTo(testArtifact1.getName());
  }

  @Test
  void shouldIgnoreExpectedArtifactsFromUnrelatedTrigger() {
    // given
    Map<String, Object> expectedArtifact = new HashMap<>();
    expectedArtifact.put("id", "id1");
    Map<String, Object> matchArtifact = new HashMap<>();
    matchArtifact.put("kind", "gcs");
    matchArtifact.put("name", "gs://test/file.yaml");
    matchArtifact.put("type", "gcs/object");
    expectedArtifact.put("matchArtifact", matchArtifact);

    Map<String, Object> pipelineTrigger = new HashMap<>();
    pipelineTrigger.put("type", "pipeline");
    pipelineTrigger.put("pipeline", "5e96d1e8-a3c0-4458-b3a4-fda17e0d5ab5");

    Map<String, Object> jenkinsTrigger = new HashMap<>();
    jenkinsTrigger.put("type", "jenkins");
    jenkinsTrigger.put("expectedArtifactIds", List.of("id1"));

    Map<String, Object> triggeredPipelineConfig = new HashMap<>();
    triggeredPipelineConfig.put("name", "triggered");
    triggeredPipelineConfig.put("id", "triggered");
    triggeredPipelineConfig.put("expectedArtifacts", List.of(expectedArtifact));
    triggeredPipelineConfig.put("triggers", List.of(pipelineTrigger, jenkinsTrigger));

    Artifact testArtifact1 =
        Artifact.builder().type("gcs/object").name("gs://test/file.yaml").build();
    Artifact testArtifact2 =
        Artifact.builder().type("docker/image").name("gcr.io/project/image").build();

    PipelineExecution parentPipeline = pipeline();
    parentPipeline.setName("parent");
    parentPipeline.setRootId("parent-root-id");
    parentPipeline.setTrigger(
        new DefaultTrigger(
            "webhook", null, "test", new HashMap<>(), List.of(testArtifact1, testArtifact2)));
    parentPipeline.setAuthentication(
        new PipelineExecution.AuthenticationDetails("parentUser", "acct1", "acct2"));
    parentPipeline.setPipelineConfigId("5e96d1e8-a3c0-4458-b3a4-fda17e0d5ab5");

    ExecutionLauncher executionLauncher = mock(ExecutionLauncher.class);
    StaticApplicationContext applicationContext = new StaticApplicationContext();
    applicationContext.getBeanFactory().registerSingleton("pipelineLauncher", executionLauncher);
    dependentPipelineStarter =
        new DependentPipelineStarter(
            applicationContext,
            mapper,
            new ContextParameterProcessor(),
            Optional.empty(),
            Optional.of(artifactUtils),
            new NoopRegistry());

    when(executionLauncher.start(eq(PIPELINE), any(), any()))
        .thenAnswer(
            invocation -> {
              Map<String, Object> p = invocation.getArgument(1);
              PipelineExecution result = pipeline();
              result.setName((String) p.get("name"));
              result.setId((String) p.get("id"));
              result.setTrigger(mapper.convertValue(p.get("trigger"), Trigger.class));
              return result;
            });

    // when
    PipelineExecution result =
        dependentPipelineStarter.trigger(
            triggeredPipelineConfig,
            null,
            parentPipeline,
            new HashMap<>(),
            null,
            buildAuthenticatedUser("user", new ArrayList<>()));

    // then
    verify(executionLauncher, never()).start(eq(PIPELINE), any());
    verify(executionLauncher).start(eq(PIPELINE), any(), eq(parentPipeline.getRootId()));

    assertThat(result.getTrigger().getArtifacts()).hasSize(2);
    assertThat(result.getTrigger().getArtifacts())
        .filteredOn(artifact -> artifact.getName().equals(testArtifact1.getName()))
        .hasSize(1);
    assertThat(result.getTrigger().getArtifacts())
        .filteredOn(artifact -> artifact.getName().equals(testArtifact2.getName()))
        .hasSize(1);
    assertThat(result.getTrigger().getResolvedExpectedArtifacts()).hasSize(1);
    assertThat(result.getTrigger().getResolvedExpectedArtifacts().get(0).getId()).isEqualTo("id1");
  }

  @Test
  void shouldFindExpectedArtifactsFromParentPipelineTriggerIfTriggeredByPipelineStage() {
    // given
    Map<String, Object> triggeredPipelineConfig = new HashMap<>();
    triggeredPipelineConfig.put("name", "triggered");
    triggeredPipelineConfig.put("id", "triggered");
    triggeredPipelineConfig.put("expectedArtifacts", List.of());
    triggeredPipelineConfig.put("triggers", List.of());

    Artifact testArtifact1 =
        Artifact.builder().type("gcs/object").name("gs://test/file.yaml").build();
    Artifact testArtifact2 =
        Artifact.builder().type("docker/image").name("gcr.io/project/image").version("42").build();
    Artifact testArtifact3 =
        Artifact.builder()
            .type("docker/image")
            .name("gcr.io/project/image")
            .version("1337")
            .build();
    PipelineExecution parentPipeline = pipeline();
    parentPipeline.setName("parent");
    parentPipeline.setRootId("parent-root-id");
    parentPipeline.setTrigger(
        new DefaultTrigger(
            "webhook", null, "test", new HashMap<>(), List.of(testArtifact1, testArtifact2)));
    parentPipeline.setAuthentication(
        new PipelineExecution.AuthenticationDetails("parentUser", "acct1", "acct2"));
    parentPipeline.setPipelineConfigId("5e96d1e8-a3c0-4458-b3a4-fda17e0d5ab5");

    StageExecution stage1 = new StageExecutionImpl(parentPipeline, "test");
    stage1.setId("stage1");
    stage1.setRefId("1");

    StageExecution stage2 = new StageExecutionImpl(parentPipeline, "test");
    stage2.setId("stage2");
    stage2.setRefId("2");
    stage2.setRequisiteStageRefIds(Collections.singletonList("1"));

    parentPipeline.getStages().add(stage1);
    parentPipeline.getStages().add(stage2);
    parentPipeline.stageByRef("1").setOutputs(Map.of("artifacts", List.of(testArtifact3)));

    String uuid = "8f241d2a-7fee-4a95-8d84-0a508222032c";
    Artifact expectedImage =
        Artifact.builder().type("docker/image").name("gcr.io/project/image").build();
    List<ExpectedArtifact> expectedArtifacts =
        List.of(ExpectedArtifact.builder().id(uuid).matchArtifact(expectedImage).build());
    parentPipeline.getTrigger().setOther("expectedArtifacts", expectedArtifacts);
    parentPipeline.getTrigger().setResolvedExpectedArtifacts(expectedArtifacts);

    ExecutionLauncher executionLauncher = mock(ExecutionLauncher.class);
    StaticApplicationContext applicationContext = new StaticApplicationContext();
    applicationContext.getBeanFactory().registerSingleton("pipelineLauncher", executionLauncher);
    dependentPipelineStarter =
        new DependentPipelineStarter(
            applicationContext,
            mapper,
            new ContextParameterProcessor(),
            Optional.empty(),
            Optional.of(artifactUtils),
            new NoopRegistry());

    when(executionLauncher.start(eq(PIPELINE), any(), any()))
        .thenAnswer(
            invocation -> {
              Map<String, Object> p = invocation.getArgument(1);
              PipelineExecution result = pipeline();
              result.setName((String) p.get("name"));
              result.setId((String) p.get("id"));
              result.setTrigger(mapper.convertValue(p.get("trigger"), Trigger.class));
              return result;
            });

    // when
    PipelineExecution result =
        dependentPipelineStarter.trigger(
            triggeredPipelineConfig,
            null,
            parentPipeline,
            new HashMap<>(),
            null,
            buildAuthenticatedUser("user", new ArrayList<>()));

    // then
    verify(executionLauncher, never()).start(eq(PIPELINE), any());
    verify(executionLauncher).start(eq(PIPELINE), any(), eq(parentPipeline.getRootId()));

    assertThat(result.getTrigger().getArtifacts()).hasSize(3);
    assertThat(result.getTrigger().getArtifacts())
        .filteredOn(artifact -> artifact.getName().equals(testArtifact1.getName()))
        .hasSize(1);
    // testArtifact2 and testArtifact3 have the same name...make sure the versions are as expected,
    // i.e. both artifacts are present.
    assertThat(result.getTrigger().getArtifacts())
        .filteredOn(artifact -> artifact.getName().equals("gcr.io/project/image"))
        .map(Artifact::getVersion)
        .containsOnly("42", "1337");
  }

  @Test
  void shouldFailPipelineWhenParentPipelineDoesNotProvideExpectedArtifacts() {
    // given
    Artifact artifact = Artifact.builder().type("embedded/base64").name("baked-manifest").build();
    String expectedArtifactId = "826018cd-e278-4493-a6a5-4b0a0166a843";
    ExpectedArtifact expectedArtifact =
        ExpectedArtifact.builder().id(expectedArtifactId).matchArtifact(artifact).build();

    PipelineExecution parentPipeline = pipeline();
    parentPipeline.setName("my-parent-pipeline");
    parentPipeline.setRootId("parent-root-id");
    parentPipeline.setAuthentication(
        new PipelineExecution.AuthenticationDetails("username", "account1"));
    parentPipeline.setPipelineConfigId("fe0b3537-3101-46a1-8e08-ab57cf65a207");

    // not passing artifacts
    StageExecution stage1 = new StageExecutionImpl(parentPipeline, "test");
    stage1.setId("my-stage-1");
    stage1.setRefId("1");

    parentPipeline.getStages().add(stage1);

    Map<String, Object> pipelineTrigger = new HashMap<>();
    pipelineTrigger.put("type", "pipeline");
    pipelineTrigger.put("pipeline", parentPipeline.getPipelineConfigId());
    pipelineTrigger.put("expectedArtifactIds", List.of(expectedArtifactId));

    Map<String, Object> triggeredPipelineConfig = new HashMap<>();
    triggeredPipelineConfig.put("name", "triggered-by-stage");
    triggeredPipelineConfig.put("id", "triggered-id");
    triggeredPipelineConfig.put(
        "stages", List.of(Map.of("name", "My Stage", "type", "bakeManifest")));
    triggeredPipelineConfig.put("expectedArtifacts", List.of(expectedArtifact));
    triggeredPipelineConfig.put("triggers", List.of(pipelineTrigger));

    ExecutionLauncher executionLauncher = mock(ExecutionLauncher.class);
    StaticApplicationContext applicationContext = new StaticApplicationContext();
    applicationContext.getBeanFactory().registerSingleton("pipelineLauncher", executionLauncher);
    dependentPipelineStarter =
        new DependentPipelineStarter(
            applicationContext,
            mapper,
            new ContextParameterProcessor(),
            Optional.empty(),
            Optional.of(artifactUtils),
            new NoopRegistry());

    // Use a list to be able to extract from within a lambda.
    List<Exception> error = new ArrayList<>();
    when(executionLauncher.fail(eq(PIPELINE), any(), any(), any()))
        .thenAnswer(
            invocation -> {
              Map<String, Object> processedPipeline = invocation.getArgument(1);
              Exception artifactError = invocation.getArgument(3);

              if (artifactError != null) {
                error.add(artifactError);
              }

              PipelineExecution result = pipeline();
              result.setName((String) processedPipeline.get("name"));
              result.setId((String) processedPipeline.get("id"));
              result.setTrigger(
                  mapper.convertValue(processedPipeline.get("trigger"), Trigger.class));
              return result;
            });
    // when
    PipelineExecution result =
        dependentPipelineStarter.trigger(
            triggeredPipelineConfig,
            null,
            parentPipeline,
            new HashMap<>(),
            "my-stage-1",
            buildAuthenticatedUser("username", new ArrayList<>()));

    // then
    verify(executionLauncher, never()).start(eq(PIPELINE), any());
    verify(executionLauncher, never()).start(eq(PIPELINE), any(), any());
    verify(executionLauncher).fail(eq(PIPELINE), any(), eq(parentPipeline.getRootId()), any());

    verify(artifactUtils).resolveArtifacts(any());
    assertThat(error).hasSize(1);
    assertThat(error.get(0)).isInstanceOf(InvalidRequestException.class);
    assertThat(error.get(0).getMessage())
        .isEqualTo("Unmatched expected artifact " + expectedArtifact + " could not be resolved.");
    assertThat(result.getTrigger().getArtifacts()).hasSize(0);
  }

  @Test
  void shouldResolveExpressionsInTrigger() {
    // given
    Map<String, Object> parameterConfig = new HashMap<>();
    parameterConfig.put("name", "a");
    parameterConfig.put("default", "${2 == 2}");

    Map<String, Object> triggeredPipelineConfig = new HashMap<>();
    triggeredPipelineConfig.put("name", "triggered");
    triggeredPipelineConfig.put("id", "triggered");
    triggeredPipelineConfig.put("parameterConfig", Collections.singletonList(parameterConfig));

    PipelineExecution parentPipeline = createParentPipelineWithManualTrigger();
    ExecutionLauncher executionLauncher = mock(ExecutionLauncher.class);

    StaticApplicationContext applicationContext = new StaticApplicationContext();
    applicationContext.getBeanFactory().registerSingleton("pipelineLauncher", executionLauncher);

    dependentPipelineStarter =
        new DependentPipelineStarter(
            applicationContext,
            mapper,
            new ContextParameterProcessor(),
            Optional.empty(),
            Optional.of(artifactUtils),
            new NoopRegistry());

    when(executionLauncher.start(eq(PIPELINE), any(), any()))
        .thenAnswer(
            invocation -> {
              Map<String, Object> config = invocation.getArgument(1);
              return createPipelineExecution(config);
            });

    // when
    PipelineExecution result =
        dependentPipelineStarter.trigger(
            triggeredPipelineConfig,
            null,
            parentPipeline,
            new HashMap<>(),
            null,
            buildAuthenticatedUser("user", Collections.emptyList()));

    // then
    verify(executionLauncher, never()).start(eq(PIPELINE), any());
    verify(executionLauncher).start(eq(PIPELINE), any(), eq(parentPipeline.getRootId()));

    assertThat(result.getTrigger().getParameters()).containsEntry("a", true);
  }

  private PipelineExecution createParentPipeline(Artifact testArtifact) {
    PipelineExecution parentPipeline = new PipelineExecutionImpl(PIPELINE, "parent");
    parentPipeline.setRootId("parent-root-id");
    parentPipeline.setTrigger(new DefaultTrigger("webhook", null, "test"));
    parentPipeline.setAuthentication(
        new PipelineExecution.AuthenticationDetails("parentUser", "acct1", "acct2"));

    StageExecution stage1 = new StageExecutionImpl(parentPipeline, "test");
    stage1.setId("stage1");
    stage1.setRefId("1");
    stage1.getOutputs().put("artifacts", Collections.singletonList(testArtifact));

    StageExecution stage2 = new StageExecutionImpl(parentPipeline, "test");
    stage2.setId("stage2");
    stage2.setRefId("2");
    stage2.setRequisiteStageRefIds(Collections.singletonList("1"));

    parentPipeline.getStages().add(stage1);
    parentPipeline.getStages().add(stage2);

    return parentPipeline;
  }

  private PipelineExecution createParentPipelineWithManualTrigger() {
    PipelineExecution parentPipeline = new PipelineExecutionImpl(PIPELINE, "testApplication");
    parentPipeline.setName("parent");
    parentPipeline.setTrigger(
        new DefaultTrigger(
            "manual",
            null,
            "fzlem@netflix.com",
            new HashMap<>(),
            Collections.emptyList(),
            Collections.emptyList(),
            false,
            true));
    parentPipeline.setAuthentication(
        new PipelineExecution.AuthenticationDetails("parentUser", "acct1", "acct2"));
    return parentPipeline;
  }

  private PipelineExecution createPipelineExecution(Map<String, Object> config) {
    PipelineExecution pipeline = new PipelineExecutionImpl(PIPELINE, "testApplication");
    pipeline.setId(config.get("id").toString());
    pipeline.setName(config.get("name").toString());
    pipeline.setTrigger(mapper.convertValue(config.get("trigger"), Trigger.class));
    return pipeline;
  }

  private PipelineExecution.AuthenticationDetails buildAuthenticatedUser(
      String email, List<String> allowedAccounts) {
    return new PipelineExecution.AuthenticationDetails(email, allowedAccounts);
  }
}
