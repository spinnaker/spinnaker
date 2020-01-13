/*
 * Copyright 2020 Netflix, Inc.
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

package com.netflix.spinnaker.orca.front50.tasks;

import com.netflix.spinnaker.orca.ExecutionStatus;
import com.netflix.spinnaker.orca.Task;
import com.netflix.spinnaker.orca.TaskResult;
import com.netflix.spinnaker.orca.front50.Front50Service;
import com.netflix.spinnaker.orca.pipeline.model.Stage;
import java.util.HashMap;
import java.util.Map;
import javax.annotation.Nonnull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import retrofit.client.Response;

@Component
public class DeletePluginInfoTask implements Task {

  private Logger log = LoggerFactory.getLogger(getClass());

  private Front50Service front50Service;

  @Autowired
  public DeletePluginInfoTask(Front50Service front50Service) {
    this.front50Service = front50Service;
  }

  @Nonnull
  @Override
  public TaskResult execute(@Nonnull Stage stage) {
    StageData stageData = stage.mapTo(StageData.class);

    if (stageData.pluginInfoId == null) {
      throw new IllegalArgumentException("Key 'pluginInfoId' must be provided.");
    }

    String pluginInfoId = stageData.pluginInfoId;

    log.debug("Deleting front50 plugin info `{}`", pluginInfoId);
    Response response = front50Service.deletePluginInfo(pluginInfoId);

    if (response.getStatus() != HttpStatus.NO_CONTENT.value()
        && response.getStatus() != HttpStatus.NOT_FOUND.value()) {
      log.warn(
          "Failed to delete `{}`, received unexpected response status `{}`",
          pluginInfoId,
          response.getStatus());
      return TaskResult.ofStatus(ExecutionStatus.TERMINAL);
    }

    Map<String, Object> outputs = new HashMap<>();
    outputs.put("front50ResponseStatus", response.getStatus());
    outputs.put("pluginInfoId", pluginInfoId);

    return TaskResult.builder(ExecutionStatus.SUCCEEDED).context(outputs).build();
  }

  private static class StageData {
    public String pluginInfoId;
  }
}
