/*
 * Copyright 2026 Harness, Inc.
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

package com.netflix.spinnaker.orca.clouddriver.tasks.providers.aws.lambda;

import static com.netflix.spinnaker.orca.api.pipeline.models.ExecutionStatus.SUCCEEDED;
import static com.netflix.spinnaker.orca.api.pipeline.models.ExecutionType.PIPELINE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.netflix.spinnaker.orca.api.pipeline.TaskResult;
import com.netflix.spinnaker.orca.clouddriver.CloudDriverCacheService;
import com.netflix.spinnaker.orca.pipeline.model.PipelineExecutionImpl;
import com.netflix.spinnaker.orca.pipeline.model.StageExecutionImpl;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import okhttp3.MediaType;
import okhttp3.ResponseBody;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import retrofit2.Call;
import retrofit2.Response;
import retrofit2.mock.Calls;

@ExtendWith(MockitoExtension.class)
class LambdaFunctionForceRefreshTaskTest {

  private static final Map<String, Object> REFRESH_BODY =
      Map.of(
          "cloudProvider", "aws",
          "account", "test-account",
          "region", "us-west-2",
          "functionName", "test-function",
          "appName", "testapp");

  @Mock private CloudDriverCacheService cacheService;

  @InjectMocks private LambdaFunctionForceRefreshTask task;

  private StageExecutionImpl stage;

  @BeforeEach
  void setup() {
    Map<String, Object> context = new HashMap<>();
    context.put("cloudProvider", "aws");
    context.put("account", "test-account");
    context.put("region", "us-west-2");
    context.put("functionName", "test-function");
    stage =
        new StageExecutionImpl(
            new PipelineExecutionImpl(PIPELINE, "testapp"), "lambdaFunction", context);
  }

  @Test
  void executesForceCacheRefreshWithTheStageContextAndApplication() {
    Call<ResponseBody> call =
        Calls.response(
            Response.success(200, ResponseBody.create(MediaType.parse("application/json"), "{}")));
    when(cacheService.forceCacheUpdate("aws", "Function", REFRESH_BODY)).thenReturn(call);

    TaskResult result = task.execute(stage);

    verify(cacheService).forceCacheUpdate("aws", "Function", REFRESH_BODY);
    assertThat(call.isExecuted()).isTrue();
    assertEquals(SUCCEEDED, result.getStatus());
    assertThat(result.getContext()).doesNotContainKey("force.cache.refresh.errors");
  }

  @Test
  void succeedsWhenTheForceCacheRefreshFails() {
    Call<ResponseBody> call = Calls.failure(new IOException("clouddriver unavailable"));
    when(cacheService.forceCacheUpdate("aws", "Function", REFRESH_BODY)).thenReturn(call);

    TaskResult result = task.execute(stage);

    assertThat(call.isExecuted()).isTrue();
    assertEquals(SUCCEEDED, result.getStatus());
    assertThat((List<String>) result.getContext().get("force.cache.refresh.errors"))
        .singleElement()
        .asString()
        .startsWith("Failed to refresh test-function in us-west-2: ")
        .contains("clouddriver unavailable");
  }
}
