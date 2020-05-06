/*
 * Copyright 2019 Google, LLC
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
 *
 */

package com.netflix.spinnaker.clouddriver.kubernetes.op.manifest;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.netflix.spinnaker.kork.artifacts.model.Artifact;
import com.netflix.spinnaker.kork.artifacts.model.Artifact.ArtifactBuilder;
import java.util.Collection;
import org.junit.jupiter.api.Test;
import org.junit.platform.runner.JUnitPlatform;
import org.junit.runner.RunWith;

@RunWith(JUnitPlatform.class)
final class ArtifactKeyTest {
  private static String TYPE = "docker/image";
  private static String NAME = "gcr.io/test/test-image";
  private static String VERSION = "latest";
  private static String REFERENCE = "gcr.io/test/test-image:latest";
  private static String ACCOUNT = "docker-registry";

  private static ArtifactBuilder defaultArtifactBuilder() {
    return Artifact.builder().type(TYPE).name(NAME).version(VERSION).reference(REFERENCE);
  }

  @Test
  public void equalsTest() {
    Artifact artifact1 = defaultArtifactBuilder().build();
    Artifact artifact2 = defaultArtifactBuilder().build();
    assertThat(ArtifactKey.fromArtifact(artifact1)).isEqualTo(ArtifactKey.fromArtifact(artifact2));
  }

  @Test
  public void equalsWithDifferentAccountsTest() {
    Artifact artifact1 = defaultArtifactBuilder().artifactAccount(ACCOUNT).build();
    Artifact artifact2 = defaultArtifactBuilder().build();
    assertThat(ArtifactKey.fromArtifact(artifact1)).isEqualTo(ArtifactKey.fromArtifact(artifact2));
  }

  @Test
  public void differentTypeTest() {
    Artifact artifact1 = defaultArtifactBuilder().type("gcs/file").build();
    Artifact artifact2 = defaultArtifactBuilder().build();
    assertThat(ArtifactKey.fromArtifact(artifact1))
        .isNotEqualTo(ArtifactKey.fromArtifact(artifact2));
  }

  @Test
  public void differentNameTest() {
    Artifact artifact1 = defaultArtifactBuilder().name("aaa").build();
    Artifact artifact2 = defaultArtifactBuilder().build();
    assertThat(ArtifactKey.fromArtifact(artifact1))
        .isNotEqualTo(ArtifactKey.fromArtifact(artifact2));
  }

  @Test
  public void differentVersionTest() {
    Artifact artifact1 = defaultArtifactBuilder().version("oldest").build();
    Artifact artifact2 = defaultArtifactBuilder().build();
    assertThat(ArtifactKey.fromArtifact(artifact1))
        .isNotEqualTo(ArtifactKey.fromArtifact(artifact2));
  }

  @Test
  public void differentLocationTest() {
    Artifact artifact1 = defaultArtifactBuilder().location("test").build();
    Artifact artifact2 = defaultArtifactBuilder().build();
    assertThat(ArtifactKey.fromArtifact(artifact1))
        .isNotEqualTo(ArtifactKey.fromArtifact(artifact2));
  }

  @Test
  public void differentReferenceTest() {
    Artifact artifact1 = defaultArtifactBuilder().reference("zzz").build();
    Artifact artifact2 = defaultArtifactBuilder().build();
    assertThat(ArtifactKey.fromArtifact(artifact1))
        .isNotEqualTo(ArtifactKey.fromArtifact(artifact2));
  }

  @Test
  public void nullSafetyTest() {
    Artifact artifact1 = defaultArtifactBuilder().build();
    Artifact artifact2 = Artifact.builder().build();
    assertThat(ArtifactKey.fromArtifact(artifact1))
        .isNotEqualTo(ArtifactKey.fromArtifact(artifact2));
  }

  @Test
  public void fromArtifactsTest() {
    Collection<Artifact> artifacts =
        ImmutableList.of(
            defaultArtifactBuilder().build(),
            defaultArtifactBuilder().build(), // duplicate of above entry
            defaultArtifactBuilder().version("oldest").build(),
            Artifact.builder().build());
    ImmutableSet<ArtifactKey> keys = ArtifactKey.fromArtifacts(artifacts);
    assertThat(keys.size()).isEqualTo(3);
    assertThat(keys)
        .containsOnly(
            ArtifactKey.fromArtifact(defaultArtifactBuilder().build()),
            ArtifactKey.fromArtifact(defaultArtifactBuilder().version("oldest").build()),
            ArtifactKey.fromArtifact(Artifact.builder().build()));
  }

  @Test
  public void fromArtifactsNullSafety() {
    ImmutableSet<ArtifactKey> keys = ArtifactKey.fromArtifacts(null);
    assertThat(keys.size()).isEqualTo(0);
  }

  @Test
  public void toStringTest() {
    ArtifactKey key = ArtifactKey.fromArtifact(defaultArtifactBuilder().build());
    assertThat(key.toString()).contains(TYPE, NAME, REFERENCE);
  }
}
