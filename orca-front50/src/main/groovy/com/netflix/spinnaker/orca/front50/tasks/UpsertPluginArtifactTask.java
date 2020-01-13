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
import com.netflix.spinnaker.orca.ExecutionStatus;
import com.netflix.spinnaker.orca.Task;
import com.netflix.spinnaker.orca.TaskResult;
import com.netflix.spinnaker.orca.front50.Front50Service;
import com.netflix.spinnaker.orca.front50.model.PluginArtifact;
import com.netflix.spinnaker.orca.pipeline.model.Stage;
import java.util.HashMap;
import java.util.Map;
import javax.annotation.Nonnull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class UpsertPluginArtifactTask implements Task {

  private Logger log = LoggerFactory.getLogger(getClass());

  private Front50Service front50Service;
  private ObjectMapper objectMapper;

  @Autowired
  public UpsertPluginArtifactTask(Front50Service front50Service, ObjectMapper objectMapper) {
    this.front50Service = front50Service;
    this.objectMapper = objectMapper;
  }

  @Nonnull
  @Override
  public TaskResult execute(@Nonnull Stage stage) {
    if (!stage.getContext().containsKey("pluginArtifact")) {
      throw new IllegalArgumentException("Key 'pluginArtifact' must be provided.");
    }

    PluginArtifact pluginArtifact =
        objectMapper.convertValue(
            stage.getContext().get("pluginArtifact"), new TypeReference<PluginArtifact>() {});

    log.debug("Upserting front50 plugin artifact `{}`", pluginArtifact.getId());
    PluginArtifact upsertedPluginArtifact = front50Service.upsertPluginArtifact(pluginArtifact);

    Map<String, Object> outputs = new HashMap<>();
    outputs.put("pluginArtifact", upsertedPluginArtifact);

    return TaskResult.builder(ExecutionStatus.SUCCEEDED).context(outputs).build();
  }
}
