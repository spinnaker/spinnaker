/*
 * Copyright 2023 DoubleCloud, Inc.
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

package com.netflix.spinnaker.orca.bakery.tasks.manifests;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spinnaker.kork.artifacts.model.Artifact;
import com.netflix.spinnaker.orca.bakery.api.BakeryService;
import com.netflix.spinnaker.orca.pipeline.model.StageExecutionImpl;
import com.netflix.spinnaker.orca.pipeline.util.ArtifactUtils;
import com.netflix.spinnaker.orca.pipeline.util.ContextParameterProcessor;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

public class CreateBakeManifestTaskTest {

  private ObjectMapper mapper =
      new ObjectMapper().disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

  private final BakeryService bakery = mock(BakeryService.class);
  private final ArtifactUtils artifactUtils = mock(ArtifactUtils.class);
  private final ContextParameterProcessor contextParameterProcessor =
      mock(ContextParameterProcessor.class);

  private CreateBakeManifestTask createBakeManifestTask =
      new CreateBakeManifestTask(artifactUtils, contextParameterProcessor, Optional.of(bakery));

  @Test
  public void shouldMapStageToContext() throws JsonProcessingException {
    String stageJson =
        "{\n"
            + "  \"expectedArtifacts\": [\n"
            + "    {\n"
            + "      \"defaultArtifact\": {\n"
            + "        \"customKind\": true,\n"
            + "        \"id\": \"bd95dd08-58a3-4012-9db5-4c4cde176e0a\"\n"
            + "      },\n"
            + "      \"displayName\": \"rare-gecko-67\",\n"
            + "      \"id\": \"ea011068-f42e-4df0-8cf0-2fad1a6fc47e\",\n"
            + "      \"matchArtifact\": {\n"
            + "        \"artifactAccount\": \"embedded-artifact\",\n"
            + "        \"id\": \"86c1ef35-0b8a-4892-a60a-82759d8aa6ad\",\n"
            + "        \"name\": \"hi\",\n"
            + "        \"type\": \"embedded/base64\"\n"
            + "      },\n"
            + "      \"useDefaultArtifact\": false,\n"
            + "      \"usePriorArtifact\": false\n"
            + "    }\n"
            + "  ],\n"
            + "  \"inputArtifacts\": [\n"
            + "    {\n"
            + "      \"account\": \"no-auth-http-account\",\n"
            + "      \"artifact\": {\n"
            + "        \"artifactAccount\": \"no-auth-http-account\",\n"
            + "        \"id\": \"c4d18108-2b3b-40b1-ba82-d22ce17e708f\",\n"
            + "        \"reference\": \"helmfile.yml\",\n"
            + "        \"type\": \"http/file\"\n"
            + "      },\n"
            + "      \"id\": null\n"
            + "    }\n"
            + "  ],\n"
            + "  \"isNew\": true,\n"
            + "  \"name\": \"BakeManifest\",\n"
            + "  \"outputName\": \"resolvedartifact\",\n"
            + "  \"helmfileFilePath\": \"helmfile.yml\",\n"
            + "  \"type\": \"createBakeManifest\",\n"
            + "  \"environment\": \"prod\",\n"
            + "  \"includeCRDs\": \"true\",\n"
            + "  \"templateRenderer\": \"helmfile\",\n"
            + "  \"namespace\": \"test\"\n"
            + "}";

    StageExecutionImpl stage = new StageExecutionImpl();
    stage.setContext(mapper.readValue(stageJson, Map.class));
    BakeManifestContext context = stage.mapTo(BakeManifestContext.class);

    assertThat(context.getInputArtifacts().size()).isEqualTo(1);
    assertThat(context.getExpectedArtifacts().size()).isEqualTo(1);
    assertThat(context.getOutputName()).isEqualTo("resolvedartifact");
    assertThat(context.getHelmfileFilePath()).isEqualTo("helmfile.yml");
    assertThat(context.getEnvironment()).isEqualTo("prod");
    assertThat(context.getIncludeCRDs()).isEqualTo(true);
    assertThat(context.getTemplateRenderer()).isEqualTo("helmfile");
  }

  @Test
  public void shouldThrowExceptionForEmptyInputArtifacts() throws JsonProcessingException {
    String stageJson =
        "{\n"
            + "  \"isNew\": true,\n"
            + "  \"name\": \"Bake Helmfile Manifest\",\n"
            + "  \"outputName\": \"hi\",\n"
            + "  \"type\": \"createBakeManifest\"\n"
            + "}";

    StageExecutionImpl stage = new StageExecutionImpl();
    stage.setContext(mapper.readValue(stageJson, Map.class));
    BakeManifestContext context = stage.mapTo(BakeManifestContext.class);
    Exception exception =
        assertThrows(
            IllegalArgumentException.class,
            () -> createBakeManifestTask.execute(stage),
            "Expected it to throw an error but it didn't");
    assertThat(exception.getMessage())
        .isEqualTo("At least one input artifact to bake must be supplied");
  }

  @Test
  public void shouldThrowExceptionForEmptyProducedArtifact() throws JsonProcessingException {
    String stageJson =
        "{\n"
            + "  \"inputArtifacts\": [\n"
            + "    {\n"
            + "      \"account\": \"no-auth-http-account\",\n"
            + "      \"artifact\": {\n"
            + "        \"artifactAccount\": \"no-auth-http-account\",\n"
            + "        \"id\": \"c4d18108-2b3b-40b1-ba82-d22ce17e708f\",\n"
            + "        \"reference\": \"helmfile.yml\",\n"
            + "        \"type\": \"http/file\"\n"
            + "      },\n"
            + "      \"id\": null\n"
            + "    }\n"
            + "  ],\n"
            + "  \"isNew\": true,\n"
            + "  \"name\": \"Bake Helmfile Manifest\",\n"
            + "  \"outputName\": \"hi\",\n"
            + "  \"type\": \"createBakeManifest\"\n"
            + "}";

    StageExecutionImpl stage = new StageExecutionImpl();
    stage.setContext(mapper.readValue(stageJson, Map.class));
    BakeManifestContext context = stage.mapTo(BakeManifestContext.class);
    when(artifactUtils.getBoundArtifactForStage(any(), any(), any()))
        .thenReturn(Artifact.builder().build())
        .thenReturn(Artifact.builder().build());
    Exception exception =
        assertThrows(
            IllegalArgumentException.class,
            () -> createBakeManifestTask.execute(stage),
            "Expected it to throw an error but it didn't");
    assertThat(exception.getMessage())
        .isEqualTo(
            "The Bake (Manifest) stage produces one embedded base64 artifact.  Please ensure that your Bake (Manifest) stage config's `Produces Artifacts` section (`expectedArtifacts` field) contains exactly one artifact.");
  }

  @Test
  public void shouldThrowErrorIfTemplateRendererDoesNotExist() throws JsonProcessingException {
    String stageJson =
        "{\n"
            + "  \"expectedArtifacts\": [\n"
            + "    {\n"
            + "      \"defaultArtifact\": {\n"
            + "        \"customKind\": true,\n"
            + "        \"id\": \"bd95dd08-58a3-4012-9db5-4c4cde176e0a\"\n"
            + "      },\n"
            + "      \"displayName\": \"rare-gecko-67\",\n"
            + "      \"id\": \"ea011068-f42e-4df0-8cf0-2fad1a6fc47e\",\n"
            + "      \"matchArtifact\": {\n"
            + "        \"artifactAccount\": \"embedded-artifact\",\n"
            + "        \"id\": \"86c1ef35-0b8a-4892-a60a-82759d8aa6ad\",\n"
            + "        \"name\": \"hi\",\n"
            + "        \"type\": \"embedded/base64\"\n"
            + "      },\n"
            + "      \"useDefaultArtifact\": false,\n"
            + "      \"usePriorArtifact\": false\n"
            + "    }\n"
            + "  ],\n"
            + "  \"inputArtifacts\": [\n"
            + "    {\n"
            + "      \"account\": \"no-auth-http-account\",\n"
            + "      \"artifact\": {\n"
            + "        \"artifactAccount\": \"no-auth-http-account\",\n"
            + "        \"id\": \"c4d18108-2b3b-40b1-ba82-d22ce17e708f\",\n"
            + "        \"reference\": \"helmfile.yml\",\n"
            + "        \"type\": \"http/file\"\n"
            + "      },\n"
            + "      \"id\": null\n"
            + "    },\n"
            + "    {\n"
            + "      \"account\": \"no-auth-http-account\",\n"
            + "      \"artifact\": {\n"
            + "        \"artifactAccount\": \"no-auth-http-account\",\n"
            + "        \"id\": \"8f546da4-d198-48c1-9806-2835f59df2b3\",\n"
            + "        \"reference\": \"values.yml\",\n"
            + "        \"type\": \"http/file\"\n"
            + "      },\n"
            + "      \"id\": null\n"
            + "    }\n"
            + "  ],\n"
            + "  \"isNew\": true,\n"
            + "  \"name\": \"BakeManifest\",\n"
            + "  \"outputName\": \"hi\",\n"
            + "  \"type\": \"bakeHelmfileManifest\",\n"
            + "  \"templateRenderer\": \"IDONOTEXIST\"\n"
            + "}";

    StageExecutionImpl stage = new StageExecutionImpl();
    stage.setContext(mapper.readValue(stageJson, Map.class));
    when(artifactUtils.getBoundArtifactForStage(any(), any(), any()))
        .thenReturn(Artifact.builder().build())
        .thenReturn(Artifact.builder().build());
    Exception exception =
        assertThrows(
            IllegalArgumentException.class,
            () -> createBakeManifestTask.execute(stage),
            "Expected it to throw an error but it didn't");
    assertThat(exception.getMessage()).isEqualTo("Invalid template renderer IDONOTEXIST");
  }

  @Test
  public void shouldNotThrowErrorIfTemplateRendererDoesExist() throws JsonProcessingException {
    String stageJson =
        "{\n"
            + "  \"expectedArtifacts\": [\n"
            + "    {\n"
            + "      \"defaultArtifact\": {\n"
            + "        \"customKind\": true,\n"
            + "        \"id\": \"bd95dd08-58a3-4012-9db5-4c4cde176e0a\"\n"
            + "      },\n"
            + "      \"displayName\": \"rare-gecko-67\",\n"
            + "      \"id\": \"ea011068-f42e-4df0-8cf0-2fad1a6fc47e\",\n"
            + "      \"matchArtifact\": {\n"
            + "        \"artifactAccount\": \"embedded-artifact\",\n"
            + "        \"id\": \"86c1ef35-0b8a-4892-a60a-82759d8aa6ad\",\n"
            + "        \"name\": \"hi\",\n"
            + "        \"type\": \"embedded/base64\"\n"
            + "      },\n"
            + "      \"useDefaultArtifact\": false,\n"
            + "      \"usePriorArtifact\": false\n"
            + "    }\n"
            + "  ],\n"
            + "  \"inputArtifacts\": [\n"
            + "    {\n"
            + "      \"account\": \"no-auth-http-account\",\n"
            + "      \"artifact\": {\n"
            + "        \"artifactAccount\": \"no-auth-http-account\",\n"
            + "        \"id\": \"c4d18108-2b3b-40b1-ba82-d22ce17e708f\",\n"
            + "        \"reference\": \"helmfile.yml\",\n"
            + "        \"type\": \"http/file\"\n"
            + "      },\n"
            + "      \"id\": null\n"
            + "    },\n"
            + "    {\n"
            + "      \"account\": \"no-auth-http-account\",\n"
            + "      \"artifact\": {\n"
            + "        \"artifactAccount\": \"no-auth-http-account\",\n"
            + "        \"id\": \"8f546da4-d198-48c1-9806-2835f59df2b3\",\n"
            + "        \"reference\": \"values.yml\",\n"
            + "        \"type\": \"http/file\"\n"
            + "      },\n"
            + "      \"id\": null\n"
            + "    }\n"
            + "  ],\n"
            + "  \"isNew\": true,\n"
            + "  \"name\": \"BakeManifest\",\n"
            + "  \"outputName\": \"hi\",\n"
            + "  \"type\": \"bakeHelmfileManifest\",\n"
            + "  \"templateRenderer\": \"helmfile\"\n"
            + "}";

    StageExecutionImpl stage = new StageExecutionImpl();
    stage.setContext(mapper.readValue(stageJson, Map.class));
    when(artifactUtils.getBoundArtifactForStage(any(), any(), any()))
        .thenReturn(Artifact.builder().build())
        .thenReturn(Artifact.builder().build());

    assertDoesNotThrow(
        () -> createBakeManifestTask.execute(stage), "No errors were expected to be thrown");
  }
}
