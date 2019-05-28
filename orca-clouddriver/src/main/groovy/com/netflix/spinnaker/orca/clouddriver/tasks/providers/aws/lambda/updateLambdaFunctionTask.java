/*
 * Copyright (c) 2019 Armory, Inc.
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

import com.google.common.collect.ImmutableMap;
import com.netflix.spinnaker.orca.ExecutionStatus;
import com.netflix.spinnaker.orca.Task;
import com.netflix.spinnaker.orca.TaskResult;
import com.netflix.spinnaker.orca.clouddriver.KatoService;
import com.netflix.spinnaker.orca.clouddriver.model.TaskId;
import com.netflix.spinnaker.orca.clouddriver.tasks.AbstractCloudProviderAwareTask;
import com.netflix.spinnaker.orca.pipeline.model.Stage;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import javax.annotation.Nonnull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class updateLambdaFunctionTask extends AbstractCloudProviderAwareTask implements Task {

  @Autowired KatoService katoService;

  public static final String TASK_NAME = "updateLambdaFunctionConfiguration";

  @Nonnull
  @Override
  public TaskResult execute(@Nonnull Stage stage) {
    String cloudProvider = getCloudProvider(stage);

    Map<String, Object> task = new HashMap<>(stage.getContext());

    Map<String, Map> operation =
        new ImmutableMap.Builder<String, Map>().put(TASK_NAME, task).build();

    TaskId taskId =
        katoService
            .requestOperations(cloudProvider, Collections.singletonList(operation))
            .toBlocking()
            .first();

    Map<String, Object> context =
        new ImmutableMap.Builder<String, Object>()
            .put("kato.result.expected", true)
            .put("kato.last.task.id", taskId)
            .build();

    return TaskResult.builder(ExecutionStatus.SUCCEEDED).context(context).build();
  }
}
