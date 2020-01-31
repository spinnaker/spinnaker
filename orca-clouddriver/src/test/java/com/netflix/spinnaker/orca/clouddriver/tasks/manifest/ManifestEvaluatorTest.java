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

package com.netflix.spinnaker.orca.clouddriver.tasks.manifest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.netflix.spinnaker.kork.artifacts.model.Artifact;
import com.netflix.spinnaker.kork.core.RetrySupport;
import com.netflix.spinnaker.orca.clouddriver.OortService;
import com.netflix.spinnaker.orca.clouddriver.tasks.manifest.ManifestContext.BindArtifact;
import com.netflix.spinnaker.orca.clouddriver.tasks.manifest.ManifestContext.Source;
import com.netflix.spinnaker.orca.pipeline.model.Stage;
import com.netflix.spinnaker.orca.pipeline.util.ArtifactUtils;
import com.netflix.spinnaker.orca.pipeline.util.ContextParameterProcessor;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import retrofit.client.Response;
import retrofit.mime.TypedString;

@ExtendWith(MockitoExtension.class)
final class ManifestEvaluatorTest {
  private ManifestEvaluator manifestEvaluator;

  @Mock private ArtifactUtils artifactUtils;
  @Mock private ContextParameterProcessor contextParameterProcessor;
  @Mock private OortService oortService;

  private final List<Map<Object, Object>> manifests =
      ImmutableList.of(
          ImmutableMap.builder()
              .put("metadata", ImmutableMap.builder().put("name", "my-manifest").build())
              .build());

  private final TypedString manifestString =
      new TypedString("{'metadata': {'name': 'my-manifest'}}");

  @BeforeEach
  void setup() {
    manifestEvaluator =
        new ManifestEvaluator(
            artifactUtils, contextParameterProcessor, oortService, new RetrySupport());
  }

  @Test
  void textManifestSuccess() {
    Stage stage = new Stage();
    DeployManifestContext context =
        DeployManifestContext.builder().source(Source.Text).manifests(manifests).build();
    ManifestEvaluator.Result result = manifestEvaluator.evaluate(stage, context);

    assertThat(result.getManifests()).isEqualTo(manifests);
  }

  @Test
  void nullTextManifestFailure() {
    Stage stage = new Stage();
    DeployManifestContext context =
        DeployManifestContext.builder().source(Source.Text).manifests(null).build();

    assertThatThrownBy(() -> manifestEvaluator.evaluate(stage, context))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void artifactManifestSuccess() {
    Stage stage = new Stage();
    Artifact manifestArtifact = Artifact.builder().artifactAccount("my-artifact-account").build();
    DeployManifestContext context =
        DeployManifestContext.builder()
            .source(Source.Artifact)
            .manifestArtifact(manifestArtifact)
            .skipExpressionEvaluation(true)
            .build();

    when(artifactUtils.getBoundArtifactForStage(stage, null, manifestArtifact))
        .thenReturn(manifestArtifact);
    when(oortService.fetchArtifact(manifestArtifact))
        .thenReturn(new Response("http://my-url", 200, "", ImmutableList.of(), manifestString));

    ManifestEvaluator.Result result = manifestEvaluator.evaluate(stage, context);
    assertThat(result.getManifests()).isEqualTo(manifests);
  }

  @Test
  void artifactManifestByIdSuccess() {
    Stage stage = new Stage();
    Artifact manifestArtifact =
        Artifact.builder()
            .artifactAccount("my-artifact-account")
            .name("my-manifest-artifact")
            .build();
    DeployManifestContext context =
        DeployManifestContext.builder()
            .manifestArtifactId("my-manifest-artifact-id")
            .skipExpressionEvaluation(true)
            .source(Source.Artifact)
            .build();

    when(artifactUtils.getBoundArtifactForStage(stage, "my-manifest-artifact-id", null))
        .thenReturn(manifestArtifact);
    when(oortService.fetchArtifact(manifestArtifact))
        .thenReturn(new Response("http://my-url", 200, "", ImmutableList.of(), manifestString));

    ManifestEvaluator.Result result = manifestEvaluator.evaluate(stage, context);
    assertThat(result.getManifests()).isEqualTo(manifests);
  }

  @Test
  void artifactManifestMissingFailure() {
    Stage stage = new Stage();
    DeployManifestContext context =
        DeployManifestContext.builder()
            .source(Source.Artifact)
            .manifestArtifactId("my-manifest-artifact-id")
            .build();
    when(artifactUtils.getBoundArtifactForStage(stage, "my-manifest-artifact-id", null))
        .thenReturn(null);

    assertThatThrownBy(() -> manifestEvaluator.evaluate(stage, context))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void artifactManifestNoAccountFailure() {
    Stage stage = new Stage();
    Artifact manifestArtifact = Artifact.builder().build();
    DeployManifestContext context =
        DeployManifestContext.builder()
            .source(Source.Artifact)
            .manifestArtifactId("my-artifact-id")
            .skipExpressionEvaluation(true)
            .build();

    when(artifactUtils.getBoundArtifactForStage(stage, "my-artifact-id", null))
        .thenReturn(manifestArtifact);

    assertThatThrownBy(() -> manifestEvaluator.evaluate(stage, context))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void artifactManifestSkipSpelEvaluation() {
    Stage stage = new Stage();
    Artifact manifestArtifact =
        Artifact.builder()
            .artifactAccount("my-artifact-account")
            .name("my-manifest-artifact")
            .build();
    DeployManifestContext context =
        DeployManifestContext.builder()
            .manifestArtifactId("my-manifest-artifact-id")
            .skipExpressionEvaluation(true)
            .source(Source.Artifact)
            .build();

    when(artifactUtils.getBoundArtifactForStage(stage, "my-manifest-artifact-id", null))
        .thenReturn(manifestArtifact);
    when(oortService.fetchArtifact(manifestArtifact))
        .thenReturn(new Response("http://my-url", 200, "", ImmutableList.of(), manifestString));

    manifestEvaluator.evaluate(stage, context);
    verifyNoInteractions(contextParameterProcessor);
  }

  @Test
  void requiredArtifacts() {
    Stage stage = new Stage();
    Artifact artifactA = Artifact.builder().name("artifact-a").build();
    Artifact artifactB = Artifact.builder().name("artifact-b").build();
    BindArtifact bindArtifact = new BindArtifact();
    bindArtifact.setExpectedArtifactId("b");
    bindArtifact.setArtifact(artifactB);
    DeployManifestContext context =
        DeployManifestContext.builder()
            .source(Source.Text)
            .manifests(manifests)
            .requiredArtifactIds(ImmutableList.of("a"))
            .requiredArtifacts(ImmutableList.of(bindArtifact))
            .skipExpressionEvaluation(true)
            .build();

    when(artifactUtils.getBoundArtifactForId(stage, "a")).thenReturn(artifactA);
    when(artifactUtils.getBoundArtifactForStage(stage, "b", artifactB)).thenReturn(artifactB);

    ManifestEvaluator.Result result = manifestEvaluator.evaluate(stage, context);
    assertThat(result.getRequiredArtifacts()).isEqualTo(ImmutableList.of(artifactA, artifactB));
  }

  @Test
  void optionalArtifacts() {
    Stage stage = new Stage();
    DeployManifestContext context =
        DeployManifestContext.builder().source(Source.Text).manifests(manifests).build();
    ImmutableList<Artifact> optionalArtifacts =
        ImmutableList.of(Artifact.builder().name("optional-artifact").build());

    when(artifactUtils.getArtifacts(stage)).thenReturn(optionalArtifacts);

    ManifestEvaluator.Result result = manifestEvaluator.evaluate(stage, context);
    assertThat(result.getOptionalArtifacts()).isEqualTo(optionalArtifacts);
  }
}
