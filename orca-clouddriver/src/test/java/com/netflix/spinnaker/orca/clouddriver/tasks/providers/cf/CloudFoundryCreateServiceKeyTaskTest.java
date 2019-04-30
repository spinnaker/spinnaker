/*
 * Copyright 2019 Pivotal, Inc.
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

package com.netflix.spinnaker.orca.clouddriver.tasks.providers.cf;

import com.google.common.collect.ImmutableMap;
import com.netflix.spinnaker.orca.ExecutionStatus;
import com.netflix.spinnaker.orca.TaskResult;
import com.netflix.spinnaker.orca.clouddriver.KatoService;
import com.netflix.spinnaker.orca.clouddriver.model.TaskId;
import com.netflix.spinnaker.orca.pipeline.model.Execution;
import com.netflix.spinnaker.orca.pipeline.model.Stage;
import org.junit.jupiter.api.Test;
import rx.Observable;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static com.netflix.spinnaker.orca.pipeline.model.Execution.ExecutionType.PIPELINE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.matches;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CloudFoundryCreateServiceKeyTaskTest {
  @Test
  void shouldMakeRequestToKatoToCreateServiceKey() {
    String type = "createServiceKey";
    KatoService kato = mock(KatoService.class);
    String cloudProvider = "my-cloud";
    String credentials = "cf-foundation";
    String region = "org > space";
    TaskId taskId = new TaskId("kato-task-id");
    Map<String, Object> context = new HashMap<>();
    context.put("cloudProvider", cloudProvider);
    context.put("credentials", credentials);
    context.put("region", region);
    context.put("serviceInstanceName", "service-instance");
    context.put("serviceKeyName", "service-key");
    when(kato.requestOperations(matches(cloudProvider),
      eq(Collections.singletonList(Collections.singletonMap(type, context)))))
      .thenReturn(Observable.from(new TaskId[] { taskId }));
    CloudFoundryCreateServiceKeyTask task = new CloudFoundryCreateServiceKeyTask(kato);

    Map<String, Object> expectedContext = new ImmutableMap.Builder<String, Object>()
      .put("notification.type", type)
      .put("kato.last.task.id", taskId)
      .put("service.region", region)
      .put("service.account", credentials)
      .build();
    TaskResult expected = TaskResult.builder(ExecutionStatus.SUCCEEDED).context(expectedContext).build();

    TaskResult result = task.execute(new Stage(
      new Execution(PIPELINE, "orca"),
      type,
      context));

    assertThat(result).isEqualToComparingFieldByFieldRecursively(expected);
  }
}
