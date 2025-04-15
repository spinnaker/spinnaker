/*
 * Copyright 2019 Pivotal, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.igor.nexus;

import static org.mockito.Mockito.*;

import com.netflix.spinnaker.igor.config.NexusProperties;
import com.netflix.spinnaker.igor.history.EchoService;
import com.netflix.spinnaker.igor.history.model.Event;
import com.netflix.spinnaker.igor.nexus.model.NexusAssetEvent;
import com.netflix.spinnaker.igor.nexus.model.NexusAssetWebhookPayload;
import com.netflix.spinnaker.igor.nexus.model.NexusRepo;
import com.netflix.spinnaker.kork.artifacts.model.Artifact;
import java.util.Collections;
import org.junit.jupiter.api.Test;
import retrofit2.mock.Calls;

class NexusEventPosterTest {

  private EchoService echoService = mock(EchoService.class);
  private NexusProperties nexusProperties = new NexusProperties();
  private NexusProperties nexusPropertiesWithoutNodeId = new NexusProperties();

  {
    final NexusRepo nexusRepo = new NexusRepo();
    nexusRepo.setName("nexus-snapshots");
    nexusRepo.setRepo("maven-snapshots");
    nexusRepo.setBaseUrl("http://localhost:8082/repository/");
    nexusRepo.setNodeId("123");
    nexusProperties.setSearches(Collections.singletonList(nexusRepo));
  }

  {
    final NexusRepo nexusRepoWithoutNodeId = new NexusRepo();
    nexusRepoWithoutNodeId.setName("nexus-snapshots");
    nexusRepoWithoutNodeId.setRepo("maven-snapshots");
    nexusRepoWithoutNodeId.setBaseUrl("http://localhost:8082/repository/");
    nexusPropertiesWithoutNodeId.setSearches(Collections.singletonList(nexusRepoWithoutNodeId));
  }

  private NexusEventPoster nexusEventPoster = new NexusEventPoster(nexusProperties, echoService);
  private NexusEventPoster nexusEventPosterWithoutNodeId =
      new NexusEventPoster(nexusPropertiesWithoutNodeId, echoService);
  final NexusAssetWebhookPayload payload = new NexusAssetWebhookPayload();

  {
    payload.setAction("CREATED");
    payload.setAsset(new NexusAssetWebhookPayload.NexusAsset());
    payload.getAsset().setFormat("maven2");
  }

  @Test
  void postArtifact() {
    payload.setRepositoryName("maven-snapshots");
    payload.getAsset().setName("com/example/demo/0.0.1-SNAPSHOT/demo-0.0.1-20190828.022502-3.pom");

    final Artifact expectedArtifact =
        Artifact.builder()
            .type("maven/file")
            .reference("com.example" + ":" + "demo" + ":" + "0.0.1-SNAPSHOT")
            .name("com.example" + ":" + "demo")
            .version("0.0.1-SNAPSHOT")
            .provenance("maven-snapshots")
            .location(
                "http://localhost:8082/service/rest/repository/browse/maven-snapshots/com/example/demo/0.0.1-SNAPSHOT/0.0.1-20190828.022502-3/")
            .build();
    Event event =
        new NexusAssetEvent(new NexusAssetEvent.Content("nexus-snapshots", expectedArtifact));

    when(echoService.postEvent(event)).thenReturn(Calls.response(""));
    nexusEventPoster.postEvent(payload);
    verify(echoService).postEvent(event);
  }

  @Test
  void postUpdatedArtifact() {
    payload.setAction("UPDATED");
    payload.setRepositoryName("maven-snapshots");
    payload.getAsset().setName("com/example/demo/0.0.1-SNAPSHOT/demo-0.0.1-20190828.022502-3.pom");

    final Artifact expectedArtifact =
        Artifact.builder()
            .type("maven/file")
            .reference("com.example" + ":" + "demo" + ":" + "0.0.1-SNAPSHOT")
            .name("com.example" + ":" + "demo")
            .version("0.0.1-SNAPSHOT")
            .provenance("maven-snapshots")
            .location(
                "http://localhost:8082/service/rest/repository/browse/maven-snapshots/com/example/demo/0.0.1-SNAPSHOT/0.0.1-20190828.022502-3/")
            .build();
    Event event =
        new NexusAssetEvent(new NexusAssetEvent.Content("nexus-snapshots", expectedArtifact));

    when(echoService.postEvent(event)).thenReturn(Calls.response(""));
    nexusEventPoster.postEvent(payload);
    verify(echoService).postEvent(event);
  }

  @Test
  void postDeletedArtifactShouldNotSendEvent() {
    payload.setAction("DELETED");
    payload.setRepositoryName("maven-snapshots");
    payload.getAsset().setName("com/example/demo/0.0.1-SNAPSHOT/demo-0.0.1-20190828.022502-3.pom");

    nexusEventPoster.postEvent(payload);

    verifyNoMoreInteractions(echoService);
  }

  @Test
  void postNonPomArtifactShouldNotSendEvent() {
    payload.setRepositoryName("maven-snapshots");
    payload.getAsset().setName("com/example/demo/0.0.1-SNAPSHOT/demo-0.0.1-20190828.022502-3.jar");

    nexusEventPoster.postEvent(payload);

    verifyNoMoreInteractions(echoService);
  }

  @Test
  void postNonMatchingRepoArtifactShouldNotSendEvent() {
    payload.setRepositoryName("DNE");
    payload.getAsset().setName("com/example/demo/0.0.1-SNAPSHOT/demo-0.0.1-20190828.022502-3.pom");

    nexusEventPoster.postEvent(payload);

    verifyNoMoreInteractions(echoService);
  }

  @Test
  void postNonMatchingRepoArtifactWithRepoId() {
    payload.setRepositoryName("DNE");
    payload.setNodeId("123");
    payload.getAsset().setName("com/example/demo/0.0.1-SNAPSHOT/demo-0.0.1-20190828.022502-3.pom");

    final Artifact expectedArtifact =
        Artifact.builder()
            .type("maven/file")
            .reference("com.example" + ":" + "demo" + ":" + "0.0.1-SNAPSHOT")
            .name("com.example" + ":" + "demo")
            .version("0.0.1-SNAPSHOT")
            .provenance("DNE")
            .location(
                "http://localhost:8082/service/rest/repository/browse/maven-snapshots/com/example/demo/0.0.1-SNAPSHOT/0.0.1-20190828.022502-3/")
            .build();
    Event event =
        new NexusAssetEvent(new NexusAssetEvent.Content("nexus-snapshots", expectedArtifact));

    when(echoService.postEvent(event)).thenReturn(Calls.response(""));
    nexusEventPoster.postEvent(payload);
    verify(echoService).postEvent(event);
  }

  @Test
  void findRepoWithNodeIdInConfig() {
    payload.setRepositoryName("maven-snapshots");
    payload.setNodeId("123");
    payload.getAsset().setName("com/example/demo/0.0.1-SNAPSHOT/demo-0.0.1-20190828.022502-3.pom");

    final Artifact expectedArtifact =
        Artifact.builder()
            .type("maven/file")
            .reference("com.example" + ":" + "demo" + ":" + "0.0.1-SNAPSHOT")
            .name("com.example" + ":" + "demo")
            .version("0.0.1-SNAPSHOT")
            .provenance("maven-snapshots")
            .location(
                "http://localhost:8082/service/rest/repository/browse/maven-snapshots/com/example/demo/0.0.1-SNAPSHOT/0.0.1-20190828.022502-3/")
            .build();
    Event event =
        new NexusAssetEvent(new NexusAssetEvent.Content("nexus-snapshots", expectedArtifact));

    when(echoService.postEvent(event)).thenReturn(Calls.response(""));
    nexusEventPosterWithoutNodeId.postEvent(payload);
    verify(echoService).postEvent(event);
  }
}
