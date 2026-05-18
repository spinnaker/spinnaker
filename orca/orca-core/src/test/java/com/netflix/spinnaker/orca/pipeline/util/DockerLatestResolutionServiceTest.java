/*
 * Copyright 2026 Moderne, Inc.
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

package com.netflix.spinnaker.orca.pipeline.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.google.common.collect.ImmutableList;
import com.netflix.spinnaker.kork.artifacts.model.Artifact;
import com.netflix.spinnaker.kork.artifacts.model.ExpectedArtifact;
import java.util.List;
import org.junit.jupiter.api.Test;

final class DockerLatestResolutionServiceTest {

  private static final String LATEST_REF =
      "123456789012.dkr.ecr.us-west-2.amazonaws.com/moderne/recipe-worker-arm64:latest";
  private static final String RESOLVED_REF =
      "123456789012.dkr.ecr.us-west-2.amazonaws.com/moderne/recipe-worker-arm64:0.147.3";

  private static Artifact dockerLatest() {
    return Artifact.builder()
        .type("docker/image")
        .name("moderne/recipe-worker-arm64")
        .version("latest")
        .reference(LATEST_REF)
        .build();
  }

  private static Artifact dockerResolved() {
    return Artifact.builder()
        .type("docker/image")
        .name("moderne/recipe-worker-arm64")
        .version("0.147.3")
        .reference(RESOLVED_REF)
        .build();
  }

  @Test
  void noResolversRegistered_passesThrough() {
    DockerLatestResolutionService service = new DockerLatestResolutionService(null);
    assertThat(service.canonicalize(dockerLatest())).isEqualTo(dockerLatest());
  }

  @Test
  void canonicalizesArtifactWhenResolverHandlesIt() {
    DockerLatestResolutionService service =
        new DockerLatestResolutionService(List.of(new StubResolver(true, dockerResolved())));
    Artifact result = service.canonicalize(dockerLatest());
    assertThat(result.getVersion()).isEqualTo("0.147.3");
    assertThat(result.getReference()).isEqualTo(RESOLVED_REF);
  }

  @Test
  void leavesNonDockerArtifactsUnchanged() {
    DockerLatestResolutionService service =
        new DockerLatestResolutionService(List.of(new StubResolver(true, dockerResolved())));
    Artifact gceImage =
        Artifact.builder().type("google/image").name("my-gce").version("latest").build();
    assertThat(service.canonicalize(gceImage)).isEqualTo(gceImage);
  }

  @Test
  void leavesAlreadyPinnedDockerArtifactsUnchanged() {
    DockerLatestResolutionService service =
        new DockerLatestResolutionService(List.of(new StubResolver(true, dockerResolved())));
    Artifact pinned =
        Artifact.builder()
            .type("docker/image")
            .name("moderne/recipe-worker-arm64")
            .version("0.147.3")
            .reference(RESOLVED_REF)
            .build();
    assertThat(service.canonicalize(pinned)).isEqualTo(pinned);
  }

  @Test
  void detectsLatestViaReferenceWhenVersionFieldEmpty() {
    DockerLatestResolutionService service =
        new DockerLatestResolutionService(List.of(new StubResolver(true, dockerResolved())));
    Artifact noVersion =
        Artifact.builder()
            .type("docker/image")
            .name("moderne/recipe-worker-arm64")
            .reference(LATEST_REF)
            .build();
    Artifact result = service.canonicalize(noVersion);
    assertThat(result.getVersion()).isEqualTo("0.147.3");
  }

  @Test
  void noHandlingResolver_passesThroughUnchanged() {
    DockerLatestResolutionService service =
        new DockerLatestResolutionService(List.of(new StubResolver(false, dockerResolved())));
    assertThat(service.canonicalize(dockerLatest())).isEqualTo(dockerLatest());
  }

  @Test
  void resolverThrows_propagates() {
    DockerLatestResolutionService service =
        new DockerLatestResolutionService(
            List.of(
                new DockerLatestResolver() {
                  @Override
                  public boolean handles(Artifact artifact) {
                    return true;
                  }

                  @Override
                  public Artifact canonicalize(Artifact artifact) {
                    throw new IllegalStateException("registry hiccup");
                  }
                }));
    assertThatThrownBy(() -> service.canonicalize(dockerLatest()))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("registry hiccup");
  }

  @Test
  void canonicalize_resolveResult_rewritesBothLists() {
    DockerLatestResolutionService service =
        new DockerLatestResolutionService(List.of(new StubResolver(true, dockerResolved())));

    ExpectedArtifact expected =
        ExpectedArtifact.builder()
            .id("img")
            .matchArtifact(Artifact.builder().type("docker/image").build())
            .boundArtifact(dockerLatest())
            .build();

    ArtifactResolver.ResolveResult input =
        ArtifactResolver.ResolveResult.create(
            ImmutableList.of(dockerLatest()), ImmutableList.of(expected));

    ArtifactResolver.ResolveResult out = service.canonicalize(input);
    assertThat(out.getResolvedArtifacts()).hasSize(1);
    assertThat(out.getResolvedArtifacts().get(0).getVersion()).isEqualTo("0.147.3");
    assertThat(out.getResolvedExpectedArtifacts().get(0).getBoundArtifact().getVersion())
        .isEqualTo("0.147.3");
  }

  private static final class StubResolver implements DockerLatestResolver {
    private final boolean handles;
    private final Artifact result;

    StubResolver(boolean handles, Artifact result) {
      this.handles = handles;
      this.result = result;
    }

    @Override
    public boolean handles(Artifact artifact) {
      return handles;
    }

    @Override
    public Artifact canonicalize(Artifact artifact) {
      return result;
    }
  }
}
