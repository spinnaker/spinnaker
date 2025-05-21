/*
 * Copyright 2023 OpsMx, Inc.
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

package com.netflix.spinnaker.orca.clouddriver.tasks.cache;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.netflix.spinnaker.kork.retrofit.exceptions.SpinnakerHttpException;
import com.netflix.spinnaker.orca.api.pipeline.TaskResult;
import com.netflix.spinnaker.orca.api.pipeline.models.ExecutionStatus;
import com.netflix.spinnaker.orca.api.pipeline.models.StageExecution;
import com.netflix.spinnaker.orca.clouddriver.CloudDriverCacheService;
import com.netflix.spinnaker.orca.pipeline.model.StageExecutionImpl;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import okhttp3.MediaType;
import okhttp3.ResponseBody;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import retrofit2.Retrofit;
import retrofit2.converter.jackson.JacksonConverterFactory;

public class ClouddriverClearAltTablespaceTaskTest {

  private CloudDriverCacheService cloudDriverCacheService;

  private ClouddriverClearAltTablespaceTask clouddriverClearAltTablespaceTask;

  @BeforeEach
  public void setup() {
    cloudDriverCacheService = mock(CloudDriverCacheService.class);
    clouddriverClearAltTablespaceTask =
        new ClouddriverClearAltTablespaceTask(cloudDriverCacheService);
  }

  @Test
  void testSpinnakerHttpExceptionHandling() {

    String namespace = "test-namespace";
    StageExecution stageExecution = new StageExecutionImpl();
    Map<String, Object> context = new HashMap<>();
    List<String> errors = new ArrayList<>();

    context.put("namespace", namespace);
    stageExecution.setContext(context);

    SpinnakerHttpException spinnakerHttpException = makeSpinnakerHttpException(400);

    errors.add(spinnakerHttpException.getMessage());

    when(cloudDriverCacheService.clearNamespace(namespace)).thenThrow(spinnakerHttpException);
    TaskResult taskResult = clouddriverClearAltTablespaceTask.execute(stageExecution);

    assertEquals(taskResult.getStatus(), ExecutionStatus.TERMINAL);
    assertTrue(taskResult.getContext().containsKey("errors"));
    assertEquals(errors.toString(), taskResult.getContext().get("errors").toString());
  }

  public static SpinnakerHttpException makeSpinnakerHttpException(int status) {
    String url = "https://some-url";
    retrofit2.Response retrofit2Response =
        retrofit2.Response.error(
            status,
            ResponseBody.create(
                MediaType.parse("application/json"), "{ \"message\": \"arbitrary message\" }"));

    Retrofit retrofit =
        new Retrofit.Builder()
            .baseUrl(url)
            .addConverterFactory(JacksonConverterFactory.create())
            .build();

    return new SpinnakerHttpException(retrofit2Response, retrofit);
  }
}
