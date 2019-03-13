/*
 * Copyright 2019 Pivotal, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.echo.pipelinetriggers.eventhandlers;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spectator.api.NoopRegistry;
import com.netflix.spinnaker.echo.model.Pipeline;
import com.netflix.spinnaker.echo.model.Trigger;
import com.netflix.spinnaker.echo.model.trigger.ArtifactoryEvent;
import com.netflix.spinnaker.kork.artifacts.model.Artifact;
import com.netflix.spinnaker.kork.artifacts.model.ExpectedArtifact;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ArtifactoryEventHandlerTest extends AbstractEventHandlerTest {

  private ArtifactoryEventHandler eventHandler = new ArtifactoryEventHandler(new NoopRegistry(), new ObjectMapper());

  @Test
  void getMatchingPipelinesTriggersEnabledArtifactoryPipeline() {
    ArtifactoryEvent artifactoryEvent = createArtifactoryEvent();
    Pipeline artifactoryPipeline = createPipelineWith(Collections.singletonList(createEnabledArtifactoryTrigger()));

    List<Pipeline> matchingPipelines = eventHandler.getMatchingPipelines(artifactoryEvent, Collections.singletonList(artifactoryPipeline));

    assertThat(matchingPipelines).hasSize(1);
    assertThat(matchingPipelines.get(0).getApplication()).isEqualTo(artifactoryPipeline.getApplication());
    assertThat(matchingPipelines.get(0).getName()).isEqualTo(artifactoryPipeline.getName());
  }

  @Test
  void getMatchingPipelinesAttachesArtifactoryTriggerAndReceivedArtifactsToPipeline() {
    ArtifactoryEvent artifactoryEvent = createArtifactoryEvent();
    Pipeline artifactoryPipeline = createPipelineWith(Arrays.asList(
      createEnabledArtifactoryTrigger(),
      createDisabledArtifactoryTrigger(),
      createEnabledGitTrigger(),
      createDisabledGitTrigger())
    );

    List<Pipeline> matchingPipelines = eventHandler.getMatchingPipelines(artifactoryEvent, Collections.singletonList(artifactoryPipeline));

    Trigger expectedTrigger = createEnabledArtifactoryTrigger();
    Artifact expectedArtifact = createArtifactoryArtifact();
    assertThat(matchingPipelines).hasSize(1);
    assertThat(matchingPipelines.get(0).getTrigger()).isEqualTo(expectedTrigger);
    assertThat(matchingPipelines.get(0).getReceivedArtifacts().get(0)).isEqualTo(expectedArtifact);
  }

  @Test
  void getMatchingPipelinesCanTriggerMultiplePipelines() {
    ArtifactoryEvent artifactoryEvent = createArtifactoryEvent();
    Pipeline artifactoryPipeline = createPipelineWith(Collections.singletonList(createEnabledArtifactoryTrigger()));

    List<Pipeline> matchingPipelines = eventHandler.getMatchingPipelines(artifactoryEvent, Arrays.asList(artifactoryPipeline, artifactoryPipeline));

    assertThat(matchingPipelines).hasSize(2);
  }

  @Test
  void getMatchingPipelinesDoesNotTriggerDisabledArtifactoryPipeline() {
    ArtifactoryEvent artifactoryEvent = createArtifactoryEvent();
    Pipeline artifactoryPipeline = createPipelineWith(Collections.singletonList(createDisabledArtifactoryTrigger()));

    List<Pipeline> matchingPipelines = eventHandler.getMatchingPipelines(artifactoryEvent, Collections.singletonList(artifactoryPipeline));

    assertThat(matchingPipelines).hasSize(0);
  }

  @Test
  void getMatchingPipelinesDoesNotTriggerEnabledNonArtifactoryPipeline() {
    ArtifactoryEvent artifactoryEvent = createArtifactoryEvent();
    Pipeline artifactoryPipeline = createPipelineWith(Collections.singletonList(createEnabledGitTrigger()));

    List<Pipeline> matchingPipelines = eventHandler.getMatchingPipelines(artifactoryEvent, Collections.singletonList(artifactoryPipeline));

    assertThat(matchingPipelines).hasSize(0);
  }

  @Test
  void getMatchingPipelinesDoesNotTriggerEnabledArtifactoryPipelineWithDifferentSearchName() {
    ArtifactoryEvent artifactoryEvent = createArtifactoryEvent();
    Pipeline artifactoryPipeline = createPipelineWith(Collections.singletonList(createEnabledArtifactoryTrigger("groovy-name")));

    List<Pipeline> matchingPipelines = eventHandler.getMatchingPipelines(artifactoryEvent, Collections.singletonList(artifactoryPipeline));

    assertThat(matchingPipelines).hasSize(0);
  }

  @Test
  void getMatchingPipelinesDoesNotTriggerEnabledArtifactoryPipelineWithDifferentExpectedArtifact() {
    ExpectedArtifact expectedArtifact = createMavenExpectedArtifact();
    ArtifactoryEvent artifactoryEvent = createArtifactoryEvent();
    Trigger trigger = createEnabledArtifactoryTriggerWithExpectedArtifactIdAndSearchName("1234");
    Pipeline artifactoryPipeline = createPipelineWith(
      Collections.singletonList(expectedArtifact),
      Collections.singletonList(trigger)
    );

    List<Pipeline> matchingPipelines = eventHandler.getMatchingPipelines(artifactoryEvent, Collections.singletonList(artifactoryPipeline));

    assertThat(matchingPipelines).hasSize(0);
  }

  @Test
  void getMatchingPipelinesDoesNotTriggerEnabledArtifactoryPipelineWithExpectedArtifactRegexNoMatch() {
    ExpectedArtifact expectedArtifact = createMavenExpectedArtifactWithRegex("com.test.spinnaker:artifact:1.1.*");
    ArtifactoryEvent artifactoryEvent = createArtifactoryEvent();
    Trigger trigger = createEnabledArtifactoryTriggerWithExpectedArtifactIdAndSearchName("1234");
    Pipeline artifactoryPipeline = createPipelineWith(
      Collections.singletonList(expectedArtifact),
      Collections.singletonList(trigger)
    );

    List<Pipeline> matchingPipelines = eventHandler.getMatchingPipelines(artifactoryEvent, Collections.singletonList(artifactoryPipeline));

    assertThat(matchingPipelines).hasSize(0);
  }

  @Test
  void getMatchingPipelinesTriggersEnabledArtifactoryPipelineWithExpectedArtifactRegexMatch() {
    ExpectedArtifact expectedArtifact = createMavenExpectedArtifactWithRegex("com.test.spinnaker:artifact:.*");
    ArtifactoryEvent artifactoryEvent = createArtifactoryEvent();
    Trigger trigger = createEnabledArtifactoryTriggerWithExpectedArtifactIdAndSearchName("1234");
    Pipeline artifactoryPipeline = createPipelineWith(
      Collections.singletonList(expectedArtifact),
      Collections.singletonList(trigger)
    );

    List<Pipeline> matchingPipelines = eventHandler.getMatchingPipelines(artifactoryEvent, Collections.singletonList(artifactoryPipeline));

    assertThat(matchingPipelines).hasSize(1);
  }
}
