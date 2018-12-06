/*
 * Copyright 2017 Lookout, Inc.
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

package com.netflix.spinnaker.clouddriver.ecs.cache.client;

import com.amazonaws.services.ecs.model.ContainerDefinition;
import com.amazonaws.services.ecs.model.TaskDefinition;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spinnaker.cats.cache.Cache;
import com.netflix.spinnaker.cats.cache.CacheData;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static com.netflix.spinnaker.clouddriver.ecs.cache.Keys.Namespace.TASK_DEFINITIONS;

@Component
public class TaskDefinitionCacheClient extends AbstractCacheClient<TaskDefinition> {
  private ObjectMapper objectMapper;

  @Autowired
  public TaskDefinitionCacheClient(Cache cacheView, ObjectMapper objectMapper) {
    super(cacheView, TASK_DEFINITIONS.toString());
    this.objectMapper = objectMapper;
  }

  @Override
  protected TaskDefinition convert(CacheData cacheData) {
    TaskDefinition taskDefinition = new TaskDefinition();
    Map<String, Object> attributes = cacheData.getAttributes();

    taskDefinition.setTaskDefinitionArn((String) attributes.get("taskDefinitionArn"));
    taskDefinition.setTaskRoleArn((String) attributes.get("taskRoleArn"));
    taskDefinition.setCpu((String) attributes.get("cpu"));
    taskDefinition.setMemory((String) attributes.get("memory"));

    if (attributes.containsKey("containerDefinitions")) {
      List<Map<String, Object>> containerDefinitions = (List<Map<String, Object>>) attributes.get("containerDefinitions");
      List<ContainerDefinition> deserializedContainerDefinitions = new ArrayList<>(containerDefinitions.size());

      for (Map<String, Object> serializedContainerDefinitions : containerDefinitions) {
        if (serializedContainerDefinitions != null) {
          deserializedContainerDefinitions.add(objectMapper.convertValue(serializedContainerDefinitions, ContainerDefinition.class));
        }
      }

      taskDefinition.setContainerDefinitions(deserializedContainerDefinitions);
    } else {
      taskDefinition.setContainerDefinitions(Collections.emptyList());
    }

    return taskDefinition;
  }
}
