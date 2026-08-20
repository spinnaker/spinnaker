/*
 * Copyright 2019 Netflix, Inc.
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

package com.netflix.spinnaker.echo.pipelinetriggers.eventhandlers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.netflix.spinnaker.echo.artifacts.ArtifactInfoService;
import com.netflix.spinnaker.echo.build.BuildInfoService;
import com.netflix.spinnaker.echo.jackson.EchoObjectMapper;
import com.netflix.spinnaker.echo.model.Pipeline;
import com.netflix.spinnaker.echo.model.Trigger;
import com.netflix.spinnaker.echo.model.trigger.ManualEvent;
import com.netflix.spinnaker.echo.pipelinetriggers.PipelineCache;
import com.netflix.spinnaker.kork.artifacts.model.Artifact;
import com.netflix.spinnaker.kork.retrofit.exceptions.SpinnakerHttpException;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;
import okhttp3.MediaType;
import okhttp3.ResponseBody;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.jackson.JacksonConverterFactory;

class ManualEventHandlerTest {

  private final com.fasterxml.jackson.databind.ObjectMapper objectMapper =
      EchoObjectMapper.getInstance();
  private BuildInfoService buildInfoService;
  private ArtifactInfoService artifactInfoService;
  private PipelineCache pipelineCache;
  private ManualEventHandler eventHandler;

  private final Artifact artifact =
      Artifact.builder()
          .type("deb")
          .customKind(false)
          .name("my-package")
          .version("v1.1.1")
          .location("https://artifactory/my-package/")
          .reference("https://artifactory/my-package/")
          .metadata(Map.of())
          .artifactAccount("account")
          .provenance("provenance")
          .uuid("123456")
          .build();

  @BeforeEach
  void setUp() {
    buildInfoService = mock(BuildInfoService.class);
    artifactInfoService = mock(ArtifactInfoService.class);
    pipelineCache = mock(PipelineCache.class);
    eventHandler =
        new ManualEventHandler(
            objectMapper,
            Optional.of(buildInfoService),
            Optional.of(artifactInfoService),
            pipelineCache);
  }

  private static Pipeline createPipelineWith(Trigger... triggers) {
    return Pipeline.builder()
        .application("application")
        .name("name")
        .id("1")
        .triggers(List.of(triggers))
        .build();
  }

  private static boolean isTruthy(Object value) {
    if (value == null) {
      return false;
    }
    if (value instanceof Map) {
      return !((Map<?, ?>) value).isEmpty();
    }
    if (value instanceof java.util.Collection) {
      return !((java.util.Collection<?>) value).isEmpty();
    }
    if (value instanceof String) {
      return !((String) value).isEmpty();
    }
    if (value instanceof Boolean) {
      return (Boolean) value;
    }
    return true;
  }

  private static SpinnakerHttpException makeSpinnakerHttpException() {
    String url = "https://some-url";
    Response<?> retrofit2Response =
        Response.error(
            404,
            ResponseBody.create(
                "{ \"message\": \"arbitrary message\" }", MediaType.get("application/json")));

    Retrofit retrofit =
        new Retrofit.Builder()
            .baseUrl(url)
            .addConverterFactory(JacksonConverterFactory.create())
            .build();

    return new SpinnakerHttpException(retrofit2Response, retrofit);
  }

  @Test
  void shouldReplaceArtifactWithFullVersionIfItExists() {
    Map<String, Object> triggerArtifact =
        Map.of("name", "my-package", "version", "v1.1.1", "location", "artifactory");

    Trigger trigger =
        Trigger.builder().enabled(true).type("artifact").artifactName("my-package").build();
    Pipeline inputPipeline = createPipelineWith(trigger);
    Trigger manualTrigger =
        Trigger.builder().type("manual").artifacts(List.of(triggerArtifact)).build();

    when(artifactInfoService.getArtifactByVersion("artifactory", "my-package", "v1.1.1"))
        .thenReturn(artifact);
    Pipeline resolvedPipeline = eventHandler.buildTrigger(inputPipeline, manualTrigger);

    assertThat(resolvedPipeline.getReceivedArtifacts()).hasSize(1);
    assertThat(resolvedPipeline.getReceivedArtifacts().get(0).getName()).isEqualTo("my-package");
    assertThat(resolvedPipeline.getReceivedArtifacts().get(0).getReference())
        .isEqualTo("https://artifactory/my-package/");
  }

  @Test
  void shouldResolveArtifactIfItExists() {
    Map<String, Object> triggerArtifact =
        Map.of("name", "my-package", "version", "v1.1.1", "location", "artifactory");
    List<Map<String, Object>> triggerArtifacts = List.of(triggerArtifact);

    when(artifactInfoService.getArtifactByVersion("artifactory", "my-package", "v1.1.1"))
        .thenReturn(artifact);
    List<Artifact> resolvedArtifacts = eventHandler.resolveArtifacts(triggerArtifacts);

    assertThat(resolvedArtifacts).hasSize(1);
    assertThat(resolvedArtifacts.get(0).getName()).isEqualTo("my-package");
  }

  @Test
  @SuppressWarnings("unchecked")
  void shouldNotResolveArtifactIfItDoesNotExist() {
    Map<String, Object> triggerArtifact =
        Map.of("name", "my-package", "version", "v2.2.2", "location", "artifactory");
    List<Map<String, Object>> triggerArtifacts = List.of(triggerArtifact);

    when(artifactInfoService.getArtifactByVersion("artifactory", "my-package", "v2.2.2"))
        .thenThrow(makeSpinnakerHttpException());
    List<Artifact> resolvedArtifacts = eventHandler.resolveArtifacts(triggerArtifacts);
    Map<String, Object> firstArtifact =
        objectMapper.convertValue(resolvedArtifacts.get(0), Map.class);
    // Mirrors Groovy's truthy semantics (`value && key != "customKind"`), where null, empty
    // strings, empty collections, and empty maps are all falsy.
    firstArtifact =
        firstArtifact.entrySet().stream()
            .filter(e -> isTruthy(e.getValue()) && !"customKind".equals(e.getKey()))
            .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

    assertThat(resolvedArtifacts).hasSize(1);
    assertThat(firstArtifact).isEqualTo(triggerArtifact);
  }

  @Test
  void shouldDoNothingWithArtifactIfItDoesNotExist() {
    Map<String, Object> triggerArtifact =
        Map.of("name", "my-package", "version", "v2.2.2", "location", "artifactory");
    Trigger trigger =
        Trigger.builder().enabled(true).type("artifact").artifactName("my-package").build();
    Pipeline inputPipeline = createPipelineWith(trigger);
    Trigger manualTrigger =
        Trigger.builder().type("manual").artifacts(List.of(triggerArtifact)).build();

    when(artifactInfoService.getArtifactByVersion("artifactory", "my-package", "v2.2.2"))
        .thenThrow(makeSpinnakerHttpException());
    Pipeline resolvedPipeline = eventHandler.buildTrigger(inputPipeline, manualTrigger);

    assertThat(resolvedPipeline.getReceivedArtifacts()).hasSize(1);
  }

  @Test
  void shouldDoNothingWithArtifactIfItDoesNotHaveTheRightFields() {
    Map<String, Object> triggerArtifact = Map.of("name", "my-package", "version", "v2.2.2");
    Trigger trigger =
        Trigger.builder().enabled(true).type("artifact").artifactName("my-package").build();
    Pipeline inputPipeline = createPipelineWith(trigger);
    Trigger manualTrigger =
        Trigger.builder().type("manual").artifacts(List.of(triggerArtifact)).build();

    Pipeline resolvedPipeline = eventHandler.buildTrigger(inputPipeline, manualTrigger);

    assertThat(resolvedPipeline.getReceivedArtifacts()).hasSize(1);
  }

  @Test
  void shouldTriggerAPipelineRefreshBeforeBuildingTheTrigger() throws TimeoutException {
    Pipeline inputPipeline =
        Pipeline.builder().application("application").name("stale").id("boop-de-boop").build();

    String user = "definitely not a robot";
    ManualEvent manualEvent = new ManualEvent();
    ManualEvent.Content content = new ManualEvent.Content();
    content.setPipelineNameOrId(inputPipeline.getId());
    content.setApplication(inputPipeline.getApplication());
    content.setTrigger(Trigger.builder().user(user).build());
    manualEvent.setContent(content);

    Pipeline freshPipeline =
        Pipeline.builder().application("application").name("fresh").id("boop-de-boop").build();

    when(pipelineCache.getPipelinesSync()).thenReturn(List.of(inputPipeline));
    when(pipelineCache.refresh(inputPipeline)).thenReturn(freshPipeline);

    List<Pipeline> materializedPipelines =
        eventHandler.getMatchingPipelines(manualEvent, pipelineCache);

    assertThat(materializedPipelines).hasSize(1);
    Pipeline materializedPipeline = materializedPipelines.get(0);
    assertThat(materializedPipeline.getName()).isEqualTo("fresh");
    assertThat(materializedPipeline.getTrigger().getUser()).isEqualTo(user);
  }

  @Test
  void doesNotRetrievePipelineByIdFromFront50IfNotInCacheUnlessConfigured()
      throws TimeoutException {
    String application = "application";
    String pipelineName = "my-pipeline-name";
    String pipelineId = "my-pipeline-id";

    Pipeline inputPipeline =
        Pipeline.builder().application(application).name(pipelineName).id(pipelineId).build();

    ManualEvent manualEvent = new ManualEvent();
    ManualEvent.Content content = new ManualEvent.Content();
    content.setPipelineNameOrId(inputPipeline.getId());
    content.setApplication(inputPipeline.getApplication());
    content.setTrigger(Trigger.builder().build());
    manualEvent.setContent(content);

    when(pipelineCache.getPipelinesSync()).thenReturn(Collections.emptyList());
    when(pipelineCache.isFilterFront50Pipelines()).thenReturn(false);

    List<Pipeline> pipelines = eventHandler.getMatchingPipelines(manualEvent, pipelineCache);

    verify(pipelineCache, times(1)).getPipelinesSync();
    verify(pipelineCache, times(1)).isFilterFront50Pipelines();
    verifyNoMoreInteractions(pipelineCache);
    assertThat(pipelines).isEmpty();
  }

  @Test
  void retrievesPipelineByNameFromFront50IfNotInCacheWhenConfigured() throws TimeoutException {
    String application = "application";
    String pipelineName = "my-pipeline-name";
    String pipelineId = "my-pipeline-id";

    Pipeline inputPipeline =
        Pipeline.builder().application(application).name(pipelineName).id(pipelineId).build();

    ManualEvent manualEvent = new ManualEvent();
    ManualEvent.Content content = new ManualEvent.Content();
    content.setPipelineNameOrId(inputPipeline.getName());
    content.setApplication(inputPipeline.getApplication());
    content.setTrigger(Trigger.builder().build());
    manualEvent.setContent(content);

    when(pipelineCache.getPipelinesSync()).thenReturn(Collections.emptyList());
    when(pipelineCache.isFilterFront50Pipelines()).thenReturn(true);
    when(pipelineCache.getPipelineByName(application, pipelineName))
        .thenReturn(Optional.of(inputPipeline));

    List<Pipeline> pipelines = eventHandler.getMatchingPipelines(manualEvent, pipelineCache);

    verify(pipelineCache, times(1)).getPipelinesSync();
    verify(pipelineCache, times(1)).isFilterFront50Pipelines();
    verify(pipelineCache, times(1)).getPipelineByName(application, pipelineName);
    verifyNoMoreInteractions(pipelineCache);

    assertThat(pipelines).hasSize(1);
    // pipelines.get(0) != inputPipeline because ManualEventHandler calls
    // buildTrigger which adds a trigger.  It's enough to compare id,
    // application, and name.  Leave examining the trigger for another test.
    assertThat(pipelines.get(0).getId()).isEqualTo(pipelineId);
    assertThat(pipelines.get(0).getApplication()).isEqualTo(application);
    assertThat(pipelines.get(0).getName()).isEqualTo(pipelineName);
  }

  @Test
  void retrievesPipelineByIdFromFront50IfNotInCacheNorAvailableByNameWhenConfigured()
      throws TimeoutException {
    String application = "application";
    String pipelineName = "my-pipeline-name";
    String pipelineId = "my-pipeline-id";

    Pipeline inputPipeline =
        Pipeline.builder().application(application).name(pipelineName).id(pipelineId).build();

    ManualEvent manualEvent = new ManualEvent();
    ManualEvent.Content content = new ManualEvent.Content();
    content.setPipelineNameOrId(inputPipeline.getId());
    content.setApplication(inputPipeline.getApplication());
    content.setTrigger(Trigger.builder().build());
    manualEvent.setContent(content);

    when(pipelineCache.getPipelinesSync()).thenReturn(Collections.emptyList());
    when(pipelineCache.isFilterFront50Pipelines()).thenReturn(true);
    when(pipelineCache.getPipelineByName(application, pipelineId)).thenReturn(Optional.empty());
    when(pipelineCache.getPipelineById(pipelineId)).thenReturn(Optional.of(inputPipeline));

    List<Pipeline> pipelines = eventHandler.getMatchingPipelines(manualEvent, pipelineCache);

    verify(pipelineCache, times(1)).getPipelinesSync();
    verify(pipelineCache, times(1)).isFilterFront50Pipelines();
    verify(pipelineCache, times(1)).getPipelineByName(application, pipelineId);
    verify(pipelineCache, times(1)).getPipelineById(pipelineId);
    verifyNoMoreInteractions(pipelineCache);

    assertThat(pipelines).hasSize(1);
    assertThat(pipelines.get(0).getId()).isEqualTo(pipelineId);
    assertThat(pipelines.get(0).getApplication()).isEqualTo(application);
    assertThat(pipelines.get(0).getName()).isEqualTo(pipelineName);
  }

  @Test
  void retrievesPipelineFromFront50AndIgnoresItIfDisabledWhenConfigured() throws TimeoutException {
    String application = "application";
    String pipelineName = "my-pipeline-name";
    String pipelineId = "my-pipeline-id";

    Pipeline inputPipeline =
        Pipeline.builder()
            .application(application)
            .name(pipelineName)
            .id(pipelineId)
            .disabled(true)
            .build();

    ManualEvent manualEvent = new ManualEvent();
    ManualEvent.Content content = new ManualEvent.Content();
    content.setPipelineNameOrId(inputPipeline.getName());
    content.setApplication(inputPipeline.getApplication());
    content.setTrigger(Trigger.builder().build());
    manualEvent.setContent(content);

    when(pipelineCache.getPipelinesSync()).thenReturn(Collections.emptyList());
    when(pipelineCache.isFilterFront50Pipelines()).thenReturn(true);
    when(pipelineCache.getPipelineByName(application, pipelineName))
        .thenReturn(Optional.of(inputPipeline));

    List<Pipeline> pipelines = eventHandler.getMatchingPipelines(manualEvent, pipelineCache);

    verify(pipelineCache, times(1)).getPipelinesSync();
    verify(pipelineCache, times(1)).isFilterFront50Pipelines();
    verify(pipelineCache, times(1)).getPipelineByName(application, pipelineName);
    verifyNoMoreInteractions(pipelineCache);

    assertThat(pipelines).isEmpty();
  }

  @Test
  void retrievesPipelineFromFront50AndRejectsItIfItDoesNotMatchTheTriggerWhenConfigured()
      throws TimeoutException {
    String application = "application";
    String pipelineName = "my-pipeline-name";
    String pipelineId = "my-pipeline-id";

    Pipeline inputPipeline =
        Pipeline.builder()
            .application(application)
            .name(pipelineName)
            .id(pipelineId)
            .disabled(true)
            .build();

    ManualEvent manualEvent = new ManualEvent();
    ManualEvent.Content content = new ManualEvent.Content();
    content.setPipelineNameOrId(inputPipeline.getName());
    content.setApplication(inputPipeline.getApplication());
    content.setTrigger(Trigger.builder().build());
    manualEvent.setContent(content);

    when(pipelineCache.getPipelinesSync()).thenReturn(Collections.emptyList());
    when(pipelineCache.isFilterFront50Pipelines()).thenReturn(true);
    // If either the name or id match, it's considered matching, so provide both
    // a different name and different id from front50.
    when(pipelineCache.getPipelineByName(application, pipelineName))
        .thenReturn(
            Optional.of(
                Pipeline.builder()
                    .application(application)
                    .name("some-other-name")
                    .id("some-other-id")
                    .build()));

    List<Pipeline> pipelines = eventHandler.getMatchingPipelines(manualEvent, pipelineCache);

    verify(pipelineCache, times(1)).getPipelinesSync();
    verify(pipelineCache, times(1)).isFilterFront50Pipelines();
    verify(pipelineCache, times(1)).getPipelineByName(application, pipelineName);
    verifyNoMoreInteractions(pipelineCache);

    assertThat(pipelines).isEmpty();
  }

  @Test
  void handlesExceptionsRetrievingThePipelineFromFront50IfNotInCacheWhenConfigured()
      throws TimeoutException {
    String application = "application";
    String pipelineName = "my-pipeline-name";
    String pipelineId = "my-pipeline-id";

    Pipeline inputPipeline =
        Pipeline.builder()
            .application(application)
            .name(pipelineName)
            .id(pipelineId)
            .disabled(true)
            .build();

    RuntimeException arbitraryException = new RuntimeException("arbitrary message");

    ManualEvent manualEvent = new ManualEvent();
    ManualEvent.Content content = new ManualEvent.Content();
    // arbitrary choice whether to use id or name
    content.setPipelineNameOrId(inputPipeline.getName());
    content.setApplication(inputPipeline.getApplication());
    content.setTrigger(Trigger.builder().build());
    manualEvent.setContent(content);

    when(pipelineCache.getPipelinesSync()).thenReturn(Collections.emptyList());
    when(pipelineCache.isFilterFront50Pipelines()).thenReturn(true);
    when(pipelineCache.getPipelineByName(application, pipelineName)).thenThrow(arbitraryException);

    List<Pipeline> pipelines = eventHandler.getMatchingPipelines(manualEvent, pipelineCache);

    verify(pipelineCache, times(1)).getPipelinesSync();
    verify(pipelineCache, times(1)).isFilterFront50Pipelines();
    verify(pipelineCache, times(1)).getPipelineByName(application, pipelineName);
    verifyNoMoreInteractions(pipelineCache);

    assertThat(pipelines).hasSize(1);
    assertThat(pipelines.get(0).getApplication()).isEqualTo(application);
    // ManualEventHandler doesn't know whether the trigger specifies a name or
    // id.  Because name is a required field in pipelines, it uses it as the
    // name.  It happens to match because that's what we specified in the
    // trigger.
    assertThat(pipelines.get(0).getName()).isEqualTo(pipelineName);
    assertThat(pipelines.get(0).getErrorMessage()).isEqualTo(arbitraryException.toString());
  }
}
