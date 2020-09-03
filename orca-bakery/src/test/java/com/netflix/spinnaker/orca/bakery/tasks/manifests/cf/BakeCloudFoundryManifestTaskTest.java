/*
 * Copyright 2020 Armory, Inc.
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

package com.netflix.spinnaker.orca.bakery.tasks.manifests.cf;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spinnaker.kork.artifacts.model.Artifact;
import com.netflix.spinnaker.orca.bakery.api.BakeryService;
import com.netflix.spinnaker.orca.bakery.api.manifests.cf.BakeCloudFoundryManifestRequest;
import com.netflix.spinnaker.orca.pipeline.model.StageExecutionImpl;
import com.netflix.spinnaker.orca.pipeline.util.ArtifactUtils;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import org.junit.Test;

public class BakeCloudFoundryManifestTaskTest {

  private ObjectMapper mapper =
      new ObjectMapper().disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

  private final BakeryService bakery = mock(BakeryService.class);
  private final ArtifactUtils artifactUtils = mock(ArtifactUtils.class);

  private BakeCloudFoundryManifestTask bakeCloudFoundryManifestTask =
      new BakeCloudFoundryManifestTask(artifactUtils, Optional.of(bakery));

  @Test
  public void shouldMapStageToContext() throws JsonProcessingException {
    String stageJson =
        "{\"expectedArtifacts\":"
            + "[{\"defaultArtifact\":{\"customKind\":true,\"id\":\"22cee094-0806-43a4-b700-7f2426079984\"},"
            + "\"displayName\":\"chilly-lionfish-73\",\"id\":\"d66d330d-9157-4fde-97ea-289243af7d4b\","
            + "\"matchArtifact\":{\"artifactAccount\":\"embedded-artifact\","
            + "\"id\":\"9c44d0b0-a67b-44f4-987b-450e89224b2e\","
            + "\"name\":\"resolvedartifact\","
            + "\"type\":\"embedded/base64\"},"
            + "\"useDefaultArtifact\":false,"
            + "\"usePriorArtifact\":false}],"
            + "\"inputArtifacts\":[{\"account\":\"no-auth-http-account\","
            + "\"artifact\":{\"artifactAccount\":\"no-auth-http-account\","
            + "\"id\":\"a91ef91e-09d3-44d4-bd8f-4369af025950\","
            + "\"reference\":\"google.manifest-template.yml\","
            + "\"type\":\"http/file\"}},"
            + "{\"account\":\"no-auth-http-account\","
            + "\"artifact\":{\"artifactAccount\":\"no-auth-http-account\","
            + "\"id\":\"e4c3e9e4-19b1-439e-a544-d71ba8c96f72\","
            + "\"reference\":\"google.variables.yml\",\"type\":\"http/file\"}}],"
            + "\"name\":\"Bake CloudFoundry Manifest\","
            + "\"outputName\":\"resolvedartifact\","
            + "\"type\":\"bakeCloudFoundryManifest\"}";

    StageExecutionImpl stage = new StageExecutionImpl();
    stage.setContext(mapper.readValue(stageJson, Map.class));
    BakeCloudFoundryManifestContext context = stage.mapTo(BakeCloudFoundryManifestContext.class);
    assertThat(context.getInputArtifacts().size()).isGreaterThanOrEqualTo(2);
    assertThat(context.getExpectedArtifacts().size()).isEqualTo(1);
    assertThat(context.getOutputName()).isEqualTo("resolvedartifact");
  }

  @Test
  public void shouldMapContextToBakeCFRequest() throws JsonProcessingException {
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
            + "        \"reference\": \"google.com\",\n"
            + "        \"type\": \"http/file\"\n"
            + "      },\n"
            + "      \"id\": null\n"
            + "    },\n"
            + "    {\n"
            + "      \"account\": \"no-auth-http-account\",\n"
            + "      \"artifact\": {\n"
            + "        \"artifactAccount\": \"no-auth-http-account\",\n"
            + "        \"id\": \"8f546da4-d198-48c1-9806-2835f59df2b3\",\n"
            + "        \"reference\": \"yahoo.com\",\n"
            + "        \"type\": \"http/file\"\n"
            + "      },\n"
            + "      \"id\": null\n"
            + "    }\n"
            + "  ],\n"
            + "  \"isNew\": true,\n"
            + "  \"name\": \"Bake CloudFoundry Manifest\",\n"
            + "  \"outputName\": \"hi\",\n"
            + "  \"type\": \"bakeCloudFoundryManifest\"\n"
            + "}";

    StageExecutionImpl stage = new StageExecutionImpl();
    stage.setContext(mapper.readValue(stageJson, Map.class));
    BakeCloudFoundryManifestContext context = stage.mapTo(BakeCloudFoundryManifestContext.class);

    List<Artifact> resolvedInputArtifacts =
        context.getInputArtifacts().stream().map(i -> i.getArtifact()).collect(Collectors.toList());

    BakeCloudFoundryManifestRequest request =
        new BakeCloudFoundryManifestRequest(
            context,
            resolvedInputArtifacts.get(0),
            resolvedInputArtifacts.subList(1, resolvedInputArtifacts.size()),
            context.getOutputName());

    assertThat(request.getOutputArtifactName()).isEqualTo("hi");
    assertThat(request.getTemplateRenderer()).isEqualTo("CF");
    assertThat(request.getManifestTemplate().getReference()).isEqualTo("google.com");
    assertThat(request.getVarsArtifacts().get(0).getReference()).isEqualTo("yahoo.com");
  }

  @Test
  public void shouldThrowExceptionForEmptyInputArtifacts() throws JsonProcessingException {
    String stageJson =
        "{\n"
            + "  \"inputArtifacts\": [\n"
            + "    {\n"
            + "      \"account\": \"\",\n"
            + "      \"id\": \"\"\n"
            + "    }\n"
            + "  ],\n"
            + "  \"isNew\": true,\n"
            + "  \"name\": \"Bake CloudFoundry Manifest\",\n"
            + "  \"outputName\": \"hi\",\n"
            + "  \"type\": \"bakeCloudFoundryManifest\"\n"
            + "}";

    StageExecutionImpl stage = new StageExecutionImpl();
    stage.setContext(mapper.readValue(stageJson, Map.class));
    BakeCloudFoundryManifestContext context = stage.mapTo(BakeCloudFoundryManifestContext.class);
    Exception exception =
        assertThrows(
            IllegalArgumentException.class,
            () -> bakeCloudFoundryManifestTask.execute(stage),
            "Expected it to throw an error but it didn't");
    assertThat(exception.getMessage())
        .isEqualTo(
            "There must be one manifest template and at least one variables artifact supplied");
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
            + "        \"reference\": \"google.com\",\n"
            + "        \"type\": \"http/file\"\n"
            + "      },\n"
            + "      \"id\": null\n"
            + "    },\n"
            + "    {\n"
            + "      \"account\": \"no-auth-http-account\",\n"
            + "      \"artifact\": {\n"
            + "        \"artifactAccount\": \"no-auth-http-account\",\n"
            + "        \"id\": \"8f546da4-d198-48c1-9806-2835f59df2b3\",\n"
            + "        \"reference\": \"yahoo.com\",\n"
            + "        \"type\": \"http/file\"\n"
            + "      },\n"
            + "      \"id\": null\n"
            + "    }\n"
            + "  ],\n"
            + "  \"isNew\": true,\n"
            + "  \"name\": \"Bake CloudFoundry Manifest\",\n"
            + "  \"outputName\": \"hi\",\n"
            + "  \"type\": \"bakeCloudFoundryManifest\"\n"
            + "}";

    StageExecutionImpl stage = new StageExecutionImpl();
    stage.setContext(mapper.readValue(stageJson, Map.class));
    BakeCloudFoundryManifestContext context = stage.mapTo(BakeCloudFoundryManifestContext.class);
    when(artifactUtils.getBoundArtifactForStage(any(), any(), any()))
        .thenReturn(Artifact.builder().build())
        .thenReturn(Artifact.builder().build());
    Exception exception =
        assertThrows(
            IllegalArgumentException.class,
            () -> bakeCloudFoundryManifestTask.execute(stage),
            "Expected it to throw an error but it didn't");
    assertThat(exception.getMessage())
        .isEqualTo(
            "The CreateCloudFoundryManifest stage produces one embedded base64 artifact.  Please ensure that your stage config's `Produces Artifacts` section (`expectedArtifacts` field) contains exactly one artifact.");
  }

  @Test
  public void shouldThrowExceptionForNameMismatch() throws JsonProcessingException {
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
            + "        \"name\": \"hello\",\n"
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
            + "        \"reference\": \"google.com\",\n"
            + "        \"type\": \"http/file\"\n"
            + "      },\n"
            + "      \"id\": null\n"
            + "    },\n"
            + "    {\n"
            + "      \"account\": \"no-auth-http-account\",\n"
            + "      \"artifact\": {\n"
            + "        \"artifactAccount\": \"no-auth-http-account\",\n"
            + "        \"id\": \"8f546da4-d198-48c1-9806-2835f59df2b3\",\n"
            + "        \"reference\": \"yahoo.com\",\n"
            + "        \"type\": \"http/file\"\n"
            + "      },\n"
            + "      \"id\": null\n"
            + "    }\n"
            + "  ],\n"
            + "  \"isNew\": true,\n"
            + "  \"name\": \"Bake CloudFoundry Manifest\",\n"
            + "  \"outputName\": \"hi\",\n"
            + "  \"type\": \"bakeCloudFoundryManifest\"\n"
            + "}";

    StageExecutionImpl stage = new StageExecutionImpl();
    stage.setContext(mapper.readValue(stageJson, Map.class));
    BakeCloudFoundryManifestContext context = stage.mapTo(BakeCloudFoundryManifestContext.class);
    when(artifactUtils.getBoundArtifactForStage(any(), any(), any()))
        .thenReturn(Artifact.builder().build())
        .thenReturn(Artifact.builder().build());
    Exception exception =
        assertThrows(
            IllegalArgumentException.class,
            () -> bakeCloudFoundryManifestTask.execute(stage),
            "Expected it to throw an error but it didn't");
    assertThat(exception.getMessage())
        .isEqualTo(
            "The name of the output manifest is required and it must match the artifact name in the Produces Artifact section.");
  }
}
