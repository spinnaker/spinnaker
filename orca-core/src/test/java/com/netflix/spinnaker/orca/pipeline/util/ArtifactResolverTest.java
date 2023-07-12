/*
 * Copyright 2020 Google, Inc.
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

package com.netflix.spinnaker.orca.pipeline.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.google.common.collect.ImmutableList;
import com.netflix.spinnaker.kork.artifacts.model.Artifact;
import com.netflix.spinnaker.kork.artifacts.model.ExpectedArtifact;
import com.netflix.spinnaker.kork.web.exceptions.InvalidRequestException;
import com.netflix.spinnaker.orca.pipeline.util.ArtifactResolver.ResolveResult;
import org.junit.jupiter.api.Test;

final class ArtifactResolverTest {
  private static final Artifact DOCKER_ARTIFACT =
      Artifact.builder().name("my-docker-image").type("docker/image").build();

  private static final Artifact GCE_IMAGE_ARTIFACT =
      Artifact.builder().name("my-gce-image").type("google/image").build();

  private static ExpectedArtifact bindArtifact(
      ExpectedArtifact expectedArtifact, Artifact artifact) {
    return expectedArtifact.toBuilder().boundArtifact(artifact).build();
  }

  @Test
  void testExactMatch() {
    ArtifactResolver resolver =
        ArtifactResolver.getInstance(
            ImmutableList.of(DOCKER_ARTIFACT, GCE_IMAGE_ARTIFACT),
            /* requireUniqueMatches= */ true);

    ExpectedArtifact expected =
        ExpectedArtifact.builder()
            .matchArtifact(Artifact.builder().type("docker/image").build())
            .build();
    ResolveResult result = resolver.resolveExpectedArtifacts(ImmutableList.of(expected));

    assertThat(result.getResolvedArtifacts()).containsExactly(DOCKER_ARTIFACT);
    assertThat(result.getResolvedExpectedArtifacts())
        .containsExactly(bindArtifact(expected, DOCKER_ARTIFACT));
  }

  @Test
  void testRegexMatch() {
    ArtifactResolver resolver =
        ArtifactResolver.getInstance(
            ImmutableList.of(DOCKER_ARTIFACT, GCE_IMAGE_ARTIFACT),
            /* requireUniqueMatches= */ true);

    ExpectedArtifact expected =
        ExpectedArtifact.builder()
            .matchArtifact(Artifact.builder().type("docker/.*").build())
            .build();
    ResolveResult result = resolver.resolveExpectedArtifacts(ImmutableList.of(expected));

    assertThat(result.getResolvedArtifacts()).containsExactly(DOCKER_ARTIFACT);
    assertThat(result.getResolvedExpectedArtifacts())
        .containsExactly(bindArtifact(expected, DOCKER_ARTIFACT));
  }

  @Test
  void testNoMatch() {
    ArtifactResolver resolver =
        ArtifactResolver.getInstance(
            ImmutableList.of(GCE_IMAGE_ARTIFACT), /* requireUniqueMatches= */ true);

    ExpectedArtifact expected =
        ExpectedArtifact.builder()
            .matchArtifact(Artifact.builder().type("docker/.*").build())
            .build();

    assertThatThrownBy(() -> resolver.resolveExpectedArtifacts(ImmutableList.of(expected)))
        .isInstanceOf(InvalidRequestException.class);
  }

  @Test
  void ignoresPriorArtifacts() {
    ArtifactResolver resolver =
        ArtifactResolver.getInstance(
            ImmutableList.of(GCE_IMAGE_ARTIFACT),
            () -> ImmutableList.of(DOCKER_ARTIFACT),
            /* requireUniqueMatches= */ true);

    ExpectedArtifact expected =
        ExpectedArtifact.builder()
            .matchArtifact(Artifact.builder().type("docker/.*").build())
            .usePriorArtifact(false)
            .build();

    assertThatThrownBy(() -> resolver.resolveExpectedArtifacts(ImmutableList.of(expected)))
        .isInstanceOf(InvalidRequestException.class);
  }

  @Test
  void resolvesFromPriorArtifacts() {
    ArtifactResolver resolver =
        ArtifactResolver.getInstance(
            ImmutableList.of(GCE_IMAGE_ARTIFACT),
            () -> ImmutableList.of(DOCKER_ARTIFACT),
            /* requireUniqueMatches= */ true);

    ExpectedArtifact expected =
        ExpectedArtifact.builder()
            .matchArtifact(Artifact.builder().type("docker/.*").build())
            .usePriorArtifact(true)
            .build();

    ResolveResult result = resolver.resolveExpectedArtifacts(ImmutableList.of(expected));

    assertThat(result.getResolvedArtifacts()).containsExactly(DOCKER_ARTIFACT);
    assertThat(result.getResolvedExpectedArtifacts())
        .containsExactly(bindArtifact(expected, DOCKER_ARTIFACT));
  }

  @Test
  void ignoresDefaultArtifact() {
    ArtifactResolver resolver =
        ArtifactResolver.getInstance(
            ImmutableList.of(GCE_IMAGE_ARTIFACT), /* requireUniqueMatches= */ true);

    ExpectedArtifact expected =
        ExpectedArtifact.builder()
            .matchArtifact(Artifact.builder().type("docker/.*").build())
            .usePriorArtifact(true)
            .useDefaultArtifact(false)
            .defaultArtifact(DOCKER_ARTIFACT)
            .build();

    assertThatThrownBy(() -> resolver.resolveExpectedArtifacts(ImmutableList.of(expected)))
        .isInstanceOf(InvalidRequestException.class);
  }

  @Test
  void resolvesFromDefaultArtifact() {
    ArtifactResolver resolver =
        ArtifactResolver.getInstance(
            ImmutableList.of(GCE_IMAGE_ARTIFACT), /* requireUniqueMatches= */ true);

    ExpectedArtifact expected =
        ExpectedArtifact.builder()
            .matchArtifact(Artifact.builder().type("docker/.*").build())
            .usePriorArtifact(true)
            .useDefaultArtifact(true)
            .defaultArtifact(DOCKER_ARTIFACT)
            .build();

    ResolveResult result = resolver.resolveExpectedArtifacts(ImmutableList.of(expected));

    assertThat(result.getResolvedArtifacts()).containsExactly(DOCKER_ARTIFACT);
    assertThat(result.getResolvedExpectedArtifacts())
        .containsExactly(bindArtifact(expected, DOCKER_ARTIFACT));
  }

  @Test
  void prefersCurrentArtifact() {
    Artifact currentArtifact = DOCKER_ARTIFACT.toBuilder().name("current").build();
    Artifact priorArtifact = DOCKER_ARTIFACT.toBuilder().name("prior").build();
    Artifact defaultArtifact = DOCKER_ARTIFACT.toBuilder().name("default").build();
    ArtifactResolver resolver =
        ArtifactResolver.getInstance(
            ImmutableList.of(currentArtifact),
            () -> ImmutableList.of(priorArtifact),
            /* requireUniqueMatches= */ true);

    ExpectedArtifact expected =
        ExpectedArtifact.builder()
            .matchArtifact(Artifact.builder().type("docker/.*").build())
            .usePriorArtifact(true)
            .useDefaultArtifact(true)
            .defaultArtifact(defaultArtifact)
            .build();

    ResolveResult result = resolver.resolveExpectedArtifacts(ImmutableList.of(expected));

    assertThat(result.getResolvedArtifacts()).containsExactly(currentArtifact);
    assertThat(result.getResolvedExpectedArtifacts())
        .containsExactly(bindArtifact(expected, currentArtifact));
  }

  @Test
  void prefersPriorArtifact() {
    Artifact priorArtifact = DOCKER_ARTIFACT.toBuilder().name("prior").build();
    Artifact defaultArtifact = DOCKER_ARTIFACT.toBuilder().name("default").build();
    ArtifactResolver resolver =
        ArtifactResolver.getInstance(
            ImmutableList.of(),
            () -> ImmutableList.of(priorArtifact),
            /* requireUniqueMatches= */ true);

    ExpectedArtifact expected =
        ExpectedArtifact.builder()
            .matchArtifact(Artifact.builder().type("docker/.*").build())
            .usePriorArtifact(true)
            .useDefaultArtifact(true)
            .defaultArtifact(defaultArtifact)
            .build();

    ResolveResult result = resolver.resolveExpectedArtifacts(ImmutableList.of(expected));

    assertThat(result.getResolvedArtifacts()).containsExactly(priorArtifact);
    assertThat(result.getResolvedExpectedArtifacts())
        .containsExactly(bindArtifact(expected, priorArtifact));
  }

  @Test
  void handlesEmptyInput() {
    ArtifactResolver resolver =
        ArtifactResolver.getInstance(ImmutableList.of(), /* requireUniqueMatches= */ true);

    ResolveResult result = resolver.resolveExpectedArtifacts(ImmutableList.of());

    assertThat(result.getResolvedArtifacts()).isEmpty();
    assertThat(result.getResolvedExpectedArtifacts()).isEmpty();
  }

  @Test
  void resolvesMultipleArtifacts() {
    ExpectedArtifact expectedGoogleArtifact =
        ExpectedArtifact.builder()
            .matchArtifact(Artifact.builder().type("google/.*").build())
            .usePriorArtifact(true)
            .build();

    ExpectedArtifact expectedDockerArtifact =
        ExpectedArtifact.builder()
            .matchArtifact(Artifact.builder().type("docker/.*").build())
            .useDefaultArtifact(true)
            .defaultArtifact(DOCKER_ARTIFACT)
            .build();

    ArtifactResolver resolver =
        ArtifactResolver.getInstance(
            ImmutableList.of(),
            () -> ImmutableList.of(GCE_IMAGE_ARTIFACT),
            /* requireUniqueMatches= */ true);

    ResolveResult result =
        resolver.resolveExpectedArtifacts(
            ImmutableList.of(expectedGoogleArtifact, expectedDockerArtifact));

    assertThat(result.getResolvedArtifacts()).containsExactly(GCE_IMAGE_ARTIFACT, DOCKER_ARTIFACT);
    assertThat(result.getResolvedExpectedArtifacts())
        .containsExactly(
            bindArtifact(expectedGoogleArtifact, GCE_IMAGE_ARTIFACT),
            bindArtifact(expectedDockerArtifact, DOCKER_ARTIFACT));
  }

  @Test
  void failsWithMultipleMatches() {
    ArtifactResolver resolver =
        ArtifactResolver.getInstance(
            ImmutableList.of(DOCKER_ARTIFACT, DOCKER_ARTIFACT), /* requireUniqueMatches= */ true);

    ExpectedArtifact expected =
        ExpectedArtifact.builder()
            .matchArtifact(Artifact.builder().type("docker/image").build())
            .build();

    assertThatThrownBy(() -> resolver.resolveExpectedArtifacts(ImmutableList.of(expected)))
        .isInstanceOf(InvalidRequestException.class);
  }

  @Test
  void allowsMultipleMatches() {
    ArtifactResolver resolver =
        ArtifactResolver.getInstance(
            ImmutableList.of(DOCKER_ARTIFACT, DOCKER_ARTIFACT), /* requireUniqueMatches= */ false);

    ExpectedArtifact expected =
        ExpectedArtifact.builder()
            .matchArtifact(Artifact.builder().type("docker/image").build())
            .build();

    ResolveResult result = resolver.resolveExpectedArtifacts(ImmutableList.of(expected));

    assertThat(result.getResolvedArtifacts()).containsExactly(DOCKER_ARTIFACT);
    assertThat(result.getResolvedExpectedArtifacts())
        .containsExactly(bindArtifact(expected, DOCKER_ARTIFACT));
  }

  @Test
  void multiplyMatchingArtifactsAreReturnedOnce() {
    ArtifactResolver resolver =
        ArtifactResolver.getInstance(
            ImmutableList.of(DOCKER_ARTIFACT, DOCKER_ARTIFACT), /* requireUniqueMatches= */ false);

    ExpectedArtifact expected =
        ExpectedArtifact.builder()
            .matchArtifact(Artifact.builder().type("docker/image").build())
            .build();

    ResolveResult result = resolver.resolveExpectedArtifacts(ImmutableList.of(expected, expected));

    // The artifact should only appear once, even though it matched two expected artifacts
    assertThat(result.getResolvedArtifacts()).containsExactly(DOCKER_ARTIFACT);
    assertThat(result.getResolvedExpectedArtifacts())
        .containsExactly(
            bindArtifact(expected, DOCKER_ARTIFACT), bindArtifact(expected, DOCKER_ARTIFACT));
  }
}
