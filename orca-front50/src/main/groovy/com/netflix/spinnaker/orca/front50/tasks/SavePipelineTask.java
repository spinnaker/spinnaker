/*
 * Copyright 2017 Netflix, Inc.
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
package com.netflix.spinnaker.orca.front50.tasks;

import com.netflix.spinnaker.orca.ExecutionStatus;
import com.netflix.spinnaker.orca.RetryableTask;
import com.netflix.spinnaker.orca.TaskResult;
import com.netflix.spinnaker.orca.front50.Front50Service;
import com.netflix.spinnaker.orca.front50.PipelineModelMutator;
import com.netflix.spinnaker.orca.pipeline.model.Stage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import retrofit.client.Response;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Component
public class SavePipelineTask implements RetryableTask {

  @Autowired(required = false)
  private Front50Service front50Service;

  @Autowired(required = false)
  private List<PipelineModelMutator> pipelineModelMutators = new ArrayList<>();

  @SuppressWarnings("unchecked")
  @Override
  public TaskResult execute(Stage stage) {
    if (front50Service == null) {
      throw new UnsupportedOperationException("Front50 is not enabled, no way to save pipeline. Fix this by setting front50.enabled: true");
    }

    if (!stage.getContext().containsKey("pipeline")) {
      throw new IllegalArgumentException("pipeline context must be provided");
    }

    Map<String, Object> pipeline = (Map<String, Object>) stage.getContext().get("pipeline");
    pipelineModelMutators.stream().filter(m -> m.supports(pipeline)).forEach(m -> m.mutate(pipeline));

    Response response = front50Service.savePipeline(pipeline);

    Map<String, Object> outputs = new HashMap<>();
    outputs.put("notification.type", "savepipeline");
    outputs.put("application", pipeline.get("application"));
    outputs.put("pipeline.name", pipeline.get("name"));

    return new TaskResult(
      (response.getStatus() == HttpStatus.OK.value()) ? ExecutionStatus.SUCCEEDED : ExecutionStatus.TERMINAL,
      outputs
    );
  }

  @Override
  public long getBackoffPeriod() {
    return 15000;
  }

  @Override
  public long getTimeout() {
    return TimeUnit.MINUTES.toMillis(1);
  }
}
