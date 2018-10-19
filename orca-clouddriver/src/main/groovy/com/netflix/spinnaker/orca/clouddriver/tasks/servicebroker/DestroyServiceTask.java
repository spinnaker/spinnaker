/*
 * Copyright 2018 Pivotal, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.orca.clouddriver.tasks.servicebroker;

import com.google.common.collect.ImmutableMap;
import com.netflix.spinnaker.orca.ExecutionStatus;
import com.netflix.spinnaker.orca.Task;
import com.netflix.spinnaker.orca.TaskResult;
import com.netflix.spinnaker.orca.clouddriver.KatoService;
import com.netflix.spinnaker.orca.clouddriver.model.TaskId;
import com.netflix.spinnaker.orca.clouddriver.tasks.AbstractCloudProviderAwareTask;
import com.netflix.spinnaker.orca.pipeline.model.Stage;
import org.springframework.stereotype.Component;

import javax.annotation.Nonnull;
import java.util.Collections;
import java.util.Map;

@Component
public class DestroyServiceTask extends AbstractCloudProviderAwareTask implements Task {

  private final KatoService kato;

  public DestroyServiceTask(KatoService kato) {
    this.kato = kato;
  }

  @Nonnull
  @Override
  public TaskResult execute(@Nonnull Stage stage) {
    String cloudProvider = getCloudProvider(stage);
    String account = getCredentials(stage);
    Map<String, Map> operation = new ImmutableMap.Builder<String, Map>()
      .put("destroyService", stage.getContext())
      .build();
    TaskId taskId = kato.requestOperations(cloudProvider, Collections.singletonList(operation)).toBlocking().first();
    Map<String, Object> outputs = new ImmutableMap.Builder<String, Object>()
      .put("notification.type", "destroyService")
      .put("kato.last.task.id", taskId)
      .put("service.region",  stage.getContext().get("region"))
      .put("service.account", account)
      .build();
    return new TaskResult(ExecutionStatus.SUCCEEDED, outputs);
  }
}
