/*
 * Copyright 2026 Harness, Inc.
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

package com.netflix.spinnaker.orca.clouddriver.tasks.providers.aws.lambda;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spinnaker.kork.artifacts.model.Artifact;
import com.netflix.spinnaker.orca.api.pipeline.models.ExecutionStatus;
import com.netflix.spinnaker.orca.api.pipeline.models.StageExecution;
import com.netflix.spinnaker.orca.clouddriver.OortService;
import com.netflix.spinnaker.orca.clouddriver.pipeline.providers.aws.lambda.LambdaStageConstants;
import com.netflix.spinnaker.orca.jackson.OrcaObjectMapper;
import com.netflix.spinnaker.orca.pipeline.model.StageExecutionImpl;
import com.netflix.spinnaker.orca.pipeline.util.ArtifactUtils;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import okhttp3.ResponseBody;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import retrofit2.mock.Calls;

@ExtendWith(MockitoExtension.class)
class LambdaResolveInvokeArtifactTaskTest {

  private static final ObjectMapper objectMapper = OrcaObjectMapper.getInstance();

  @Mock private ArtifactUtils artifactUtils;

  @Mock private OortService oortService;

  private LambdaResolveInvokeArtifactTask task;

  @BeforeEach
  void setUp() {
    task = new LambdaResolveInvokeArtifactTask(artifactUtils, oortService, objectMapper);
  }

  @Test
  void execute_resolvesInlineArtifactAndStoresPayload() throws IOException {
    Map<String, Object> context = new HashMap<>();
    context.put(
        "payloadArtifact",
        Map.of(
            "id", "",
            "account", "my-account",
            "artifact",
                Map.of(
                    "type", "s3/object",
                    "reference", "s3://bucket/payload.json",
                    "artifactAccount", "s3-account")));

    StageExecution stage = new StageExecutionImpl();
    stage.setContext(context);

    Artifact resolvedArtifact =
        Artifact.builder()
            .type("s3/object")
            .reference("s3://bucket/payload.json")
            .artifactAccount("s3-account")
            .build();

    when(artifactUtils.getBoundArtifactForStage(any(), any(), any())).thenReturn(resolvedArtifact);
    when(oortService.fetchArtifact(resolvedArtifact))
        .thenReturn(
            Calls.response(
                ResponseBody.create(
                    okhttp3.MediaType.parse("application/json"), "{\"key\": \"value\"}")));

    var result = task.execute(stage);

    assertEquals(ExecutionStatus.SUCCEEDED, result.getStatus());
    assertEquals(
        "{\"key\": \"value\"}", result.getContext().get(LambdaStageConstants.resolvedPayloadKey));
    assertTrue(result.getContext().containsKey(LambdaStageConstants.resolvedPayloadArtifactKey));
  }

  @Test
  void execute_noPayloadArtifact_skips() {
    Map<String, Object> context = new HashMap<>();
    StageExecution stage = new StageExecutionImpl();
    stage.setContext(context);

    var result = task.execute(stage);

    assertEquals(ExecutionStatus.SUCCEEDED, result.getStatus());
    assertTrue(result.getContext().isEmpty());
  }

  @Test
  void execute_unresolvableArtifact_throws() {
    Map<String, Object> context = new HashMap<>();
    context.put(
        "payloadArtifact",
        Map.of(
            "id", "expected-artifact-id",
            "account", "",
            "artifact", Map.of()));

    StageExecution stage = new StageExecutionImpl();
    stage.setContext(context);

    when(artifactUtils.getBoundArtifactForStage(any(), any(), any())).thenReturn(null);

    assertThrows(IllegalStateException.class, () -> task.execute(stage));
  }
}
