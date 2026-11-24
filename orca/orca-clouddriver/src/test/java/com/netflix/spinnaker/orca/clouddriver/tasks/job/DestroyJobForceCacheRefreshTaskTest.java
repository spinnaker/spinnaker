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

package com.netflix.spinnaker.orca.clouddriver.tasks.job;

import static com.netflix.spinnaker.orca.api.pipeline.models.ExecutionStatus.SUCCEEDED;
import static com.netflix.spinnaker.orca.api.pipeline.models.ExecutionType.PIPELINE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
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
import retrofit2.Call;
import retrofit2.Response;
import retrofit2.mock.Calls;

class DestroyJobForceCacheRefreshTaskTest {

  private static final Map<String, Object> REFRESH_BODY =
      Map.of(
          "jobName",
          "job test-job",
          "region",
          "test-namespace",
          "account",
          "test-account",
          "evict",
          true);

  private final CloudDriverCacheService cacheService = mock(CloudDriverCacheService.class);
  private final DestroyJobForceCacheRefreshTask task = new DestroyJobForceCacheRefreshTask();
  private StageExecutionImpl stage;

  @BeforeEach
  void setup() {
    task.setCacheService(cacheService);

    Map<String, Object> context = new HashMap<>();
    context.put("cloudProvider", "kubernetes");
    context.put("credentials", "test-account");
    context.put("jobName", "job test-job");
    context.put("region", "test-namespace");
    stage =
        new StageExecutionImpl(
            new PipelineExecutionImpl(PIPELINE, "testapp"), "destroyJob", context);
  }

  @Test
  void executesForceCacheRefreshForTheDestroyedJob() {
    Call<ResponseBody> call =
        Calls.response(
            Response.success(200, ResponseBody.create(MediaType.parse("application/json"), "{}")));
    when(cacheService.forceCacheUpdate("kubernetes", "Job", REFRESH_BODY)).thenReturn(call);

    TaskResult result = task.execute(stage);

    verify(cacheService).forceCacheUpdate("kubernetes", "Job", REFRESH_BODY);
    assertThat(call.isExecuted()).isTrue();
    assertEquals(SUCCEEDED, result.getStatus());
    assertThat(result.getContext()).doesNotContainKey("force.cache.refresh.errors");
  }

  @Test
  void succeedsWhenTheForceCacheRefreshFails() {
    Call<ResponseBody> call = Calls.failure(new IOException("clouddriver unavailable"));
    when(cacheService.forceCacheUpdate("kubernetes", "Job", REFRESH_BODY)).thenReturn(call);

    TaskResult result = task.execute(stage);

    assertThat(call.isExecuted()).isTrue();
    assertEquals(SUCCEEDED, result.getStatus());
    assertThat((List<String>) result.getContext().get("force.cache.refresh.errors"))
        .singleElement()
        .asString()
        .startsWith("Failed to refresh job test-job in test-namespace: ")
        .contains("clouddriver unavailable");
  }
}
