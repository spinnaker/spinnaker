/*
 * Copyright 2016 Netflix, Inc.
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

package com.netflix.spinnaker.orca.clouddriver.tasks.loadbalancer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spinnaker.orca.DefaultTaskResult;
import com.netflix.spinnaker.orca.ExecutionStatus;
import com.netflix.spinnaker.orca.RetryableTask;
import com.netflix.spinnaker.orca.TaskResult;
import com.netflix.spinnaker.orca.clouddriver.KatoService;
import com.netflix.spinnaker.orca.clouddriver.model.TaskId;
import com.netflix.spinnaker.orca.clouddriver.tasks.AbstractCloudProviderAwareTask;
import com.netflix.spinnaker.orca.pipeline.model.Stage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class MigrateLoadBalancerTask extends AbstractCloudProviderAwareTask implements RetryableTask {

  static final String CLOUD_OPERATION_TYPE = "migrateLoadBalancer";

  @Override
  public long getBackoffPeriod() {
    return 2000L;
  }

  @Override
  public long getTimeout() {
    return 300000;
  }

  @Autowired
  KatoService kato;

  @Autowired
  ObjectMapper mapper;

  @Override
  public TaskResult execute(Stage stage) {
    String cloudProvider = getCloudProvider(stage);

    Map<String, Map> operation = new HashMap<>();
    operation.put(CLOUD_OPERATION_TYPE, new HashMap<>(stage.getContext()));

    List<Map<String, Map>> operations = new ArrayList<>();
    operations.add(operation);

    TaskId taskId = kato.requestOperations(cloudProvider, operations)
      .toBlocking()
      .first();

    Map<String, Object> outputs = new HashMap<>();
    outputs.put("notification.type", CLOUD_OPERATION_TYPE.toLowerCase());
    outputs.put("kato.last.task.id", taskId);
    return new DefaultTaskResult(ExecutionStatus.SUCCEEDED, outputs);
  }
}
