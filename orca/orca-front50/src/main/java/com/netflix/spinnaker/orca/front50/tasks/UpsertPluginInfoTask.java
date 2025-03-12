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

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spinnaker.orca.api.pipeline.Task;
import com.netflix.spinnaker.orca.api.pipeline.TaskResult;
import com.netflix.spinnaker.orca.api.pipeline.models.ExecutionStatus;
import com.netflix.spinnaker.orca.api.pipeline.models.StageExecution;
import com.netflix.spinnaker.orca.front50.Front50Service;
import com.netflix.spinnaker.orca.front50.model.PluginInfo;
import java.util.HashMap;
import java.util.Map;
import javax.annotation.Nonnull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class UpsertPluginInfoTask implements Task {

  private Logger log = LoggerFactory.getLogger(getClass());

  private Front50Service front50Service;
  private ObjectMapper objectMapper;

  @Autowired
  public UpsertPluginInfoTask(Front50Service front50Service, ObjectMapper objectMapper) {
    this.front50Service = front50Service;
    this.objectMapper = objectMapper;
  }

  @Nonnull
  @Override
  public TaskResult execute(@Nonnull StageExecution stage) {
    if (!stage.getContext().containsKey("pluginInfo")) {
      throw new IllegalArgumentException("Key 'pluginInfo' must be provided.");
    }

    PluginInfo pluginInfo =
        objectMapper.convertValue(
            stage.getContext().get("pluginInfo"), new TypeReference<PluginInfo>() {});

    log.debug("Upserting front50 plugin info `{}`", pluginInfo.getId());
    PluginInfo upsertedPluginInfo = front50Service.upsertPluginInfo(pluginInfo);

    Map<String, Object> outputs = new HashMap<>();
    outputs.put("pluginInfo", upsertedPluginInfo);

    return TaskResult.builder(ExecutionStatus.SUCCEEDED).context(outputs).build();
  }
}
