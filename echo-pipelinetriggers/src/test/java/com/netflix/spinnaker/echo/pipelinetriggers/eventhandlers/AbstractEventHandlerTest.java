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

import com.netflix.spinnaker.echo.model.Metadata;
import com.netflix.spinnaker.echo.model.Pipeline;
import com.netflix.spinnaker.echo.model.Trigger;
import com.netflix.spinnaker.echo.model.trigger.ArtifactoryEvent;
import com.netflix.spinnaker.kork.artifacts.model.Artifact;
import com.netflix.spinnaker.kork.artifacts.model.ExpectedArtifact;

import java.util.Collections;
import java.util.List;

abstract class AbstractEventHandlerTest {

  ArtifactoryEvent createArtifactoryEvent() {
    ArtifactoryEvent artifactoryEvent = new ArtifactoryEvent();

    ArtifactoryEvent.Content content = new ArtifactoryEvent.Content();
    content.setName("artifactorySearchName");
    content.setArtifact(createArtifactoryArtifact());

    Metadata metadata = new Metadata();
    metadata.setType(ArtifactoryEvent.TYPE);

    artifactoryEvent.setContent(content);
    artifactoryEvent.setDetails(metadata);
    return artifactoryEvent;
  }

  Artifact createArtifactoryArtifact() {
    return Artifact.builder()
      .type("maven/file")
      .reference("com.test.spinnaker:artifact:0.1.0-dev")
      .name("com.test.spinnaker:artifact")
      .version("0.1.0-dev")
      .provenance("repo")
      .build();
  }

  ExpectedArtifact createMavenExpectedArtifact() {
    return ExpectedArtifact.builder()
      .matchArtifact(Artifact.builder()
        .type("maven/file")
        .uuid("1234")
        .name("com.test.spinnaker:artifact")
        .reference("com.test.spinnaker:artifact:0.2.0-dev")
        .build())
      .build();
  }

  ExpectedArtifact createMavenExpectedArtifactWithRegex(String regex) {
    return ExpectedArtifact.builder()
      .id("1234")
      .matchArtifact(Artifact.builder()
        .type("maven/file")
        .name("com.test.spinnaker:artifact")
        .reference(regex)
        .build())
      .build();
  }

  Pipeline createPipelineWith(List<Trigger> triggers) {
    return Pipeline.builder()
      .application("application")
      .name("name")
      .triggers(triggers)
      .build();
  }

  Pipeline createPipelineWith(List<ExpectedArtifact> expectedArtifacts, List<Trigger> triggers) {
    return Pipeline.builder()
      .application("application")
      .name("name")
      .triggers(triggers)
      .expectedArtifacts(expectedArtifacts)
      .build();
  }

  Trigger createEnabledArtifactoryTrigger() {
    return createEnabledArtifactoryTrigger("artifactorySearchName");
  }

  Trigger createEnabledArtifactoryTrigger(String searchName) {
    return Trigger.builder()
      .enabled(true)
      .type("artifactory")
      .artifactorySearchName(searchName)
      .build();
  }

  Trigger createEnabledArtifactoryTriggerWithExpectedArtifactIdAndSearchName(String expectedArtifactId) {
    return Trigger.builder()
      .enabled(true)
      .type("artifactory")
      .artifactorySearchName("artifactorySearchName")
      .expectedArtifactIds(Collections.singletonList(expectedArtifactId))
      .build();
  }

  Trigger createDisabledArtifactoryTrigger() {
    return Trigger.builder()
      .enabled(false)
      .type("artifactory")
      .artifactorySearchName("artifactorySearchName")
      .build();
  }

  Trigger createEnabledGitTrigger() {
    return Trigger.builder()
      .enabled(true)
      .type("git")
      .source("bitbucket")
      .project("project")
      .slug("slug")
      .build();
  }

  Trigger createDisabledGitTrigger() {
    return Trigger.builder()
      .enabled(false)
      .type("git")
      .source("bitbucket")
      .project("project")
      .slug("slug")
      .build();
  }
}
