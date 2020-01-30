/*
 *  Copyright 2019 Pivotal, Inc.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package com.netflix.spinnaker.orca.clouddriver.tasks.providers.cf;

import static com.netflix.spinnaker.orca.pipeline.model.Execution.ExecutionType.PIPELINE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.matches;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.google.common.collect.ImmutableMap;
import com.netflix.spinnaker.orca.ExecutionStatus;
import com.netflix.spinnaker.orca.TaskResult;
import com.netflix.spinnaker.orca.clouddriver.KatoService;
import com.netflix.spinnaker.orca.clouddriver.model.Task;
import com.netflix.spinnaker.orca.clouddriver.model.TaskId;
import com.netflix.spinnaker.orca.pipeline.model.Execution;
import com.netflix.spinnaker.orca.pipeline.model.Stage;
import java.util.*;
import javax.annotation.Nullable;
import org.junit.jupiter.api.Test;

class CloudFoundryMonitorKatoServicesTaskTest {
  private void testKatoServiceStatus(
      boolean completed,
      boolean failed,
      @Nullable List<Map> resultObjects,
      ExecutionStatus expectedStatus) {
    KatoService katoService = mock(KatoService.class);
    String taskIdString = "kato-task-id";
    String credentials = "my-account";
    String cloudProvider = "cloud";
    String region = "org > space";
    when(katoService.lookupTask(matches(taskIdString), eq(true)))
        .thenReturn(
            new Task(
                taskIdString,
                new Task.Status(completed, failed, false),
                resultObjects,
                Collections.emptyList()));

    CloudFoundryMonitorKatoServicesTask task = new CloudFoundryMonitorKatoServicesTask(katoService);

    ImmutableMap.Builder<String, Object> katoTaskMapBuilder =
        new ImmutableMap.Builder<String, Object>()
            .put("id", taskIdString)
            .put("status", new Task.Status(completed, failed, false))
            .put("history", Collections.emptyList())
            .put(
                "resultObjects",
                Optional.ofNullable(resultObjects).orElse(Collections.emptyList()));
    Optional.ofNullable(resultObjects)
        .ifPresent(
            results ->
                results.stream()
                    .filter(result -> "EXCEPTION".equals(result.get("type")))
                    .findFirst()
                    .ifPresent(r -> katoTaskMapBuilder.put("exception", r)));

    Map<String, Object> expectedContext = new HashMap<>();
    TaskId taskId = new TaskId(taskIdString);
    expectedContext.put("kato.last.task.id", taskId);
    expectedContext.put("kato.task.firstNotFoundRetry", -1L);
    expectedContext.put("kato.task.notFoundRetryCount", 0);
    expectedContext.put("kato.tasks", Collections.singletonList(katoTaskMapBuilder.build()));
    TaskResult expected = TaskResult.builder(expectedStatus).context(expectedContext).build();

    Map<String, Object> context = new HashMap<>();
    context.put("cloudProvider", cloudProvider);
    context.put("kato.last.task.id", taskId);
    context.put("credentials", credentials);
    context.put("region", region);

    TaskResult result =
        task.execute(new Stage(new Execution(PIPELINE, "orca"), "deployService", context));

    assertThat(result).isEqualToComparingFieldByFieldRecursively(expected);
  }

  @Test
  void returnsStatusRunningWhenIncompleteAndNotFailedWithEmptyResults() {
    testKatoServiceStatus(false, false, Collections.emptyList(), ExecutionStatus.RUNNING);
  }

  @Test
  void returnsStatusRunningWhenCompleteAndNotFailedWithNullResults() {
    testKatoServiceStatus(true, false, null, ExecutionStatus.RUNNING);
  }

  @Test
  void returnsStatusRunningWhenCompleteAndNotFailedWithEmptyResults() {
    testKatoServiceStatus(true, false, Collections.emptyList(), ExecutionStatus.RUNNING);
  }

  @Test
  void returnsStatusTerminalWhenCompleteAndFailedWithEmptyResults() {
    testKatoServiceStatus(true, true, Collections.emptyList(), ExecutionStatus.TERMINAL);
  }

  @Test
  void returnsStatusSucceededWhenCompleteAndNotFailedWithAResult() {
    Map<String, Object> inProgressResult =
        new ImmutableMap.Builder<String, Object>()
            .put("type", "CREATE")
            .put("state", "IN_PROGRESS")
            .put("serviceInstanceName", "service-instance-name")
            .build();
    testKatoServiceStatus(
        true, true, Collections.singletonList(inProgressResult), ExecutionStatus.TERMINAL);
  }

  @Test
  void returnsStatusTerminalWithExceptionWhenCompleteAndailedWithAnExceptionResult() {
    Map<String, Object> inProgressResult =
        new ImmutableMap.Builder<String, Object>()
            .put("type", "EXCEPTION")
            .put("operation", "my-atomic-operation")
            .put("cause", "MyException")
            .put("message", "Epic Failure")
            .build();
    testKatoServiceStatus(
        true, true, Collections.singletonList(inProgressResult), ExecutionStatus.TERMINAL);
  }
}
