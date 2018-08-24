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

import com.amazonaws.services.ecs.model.Container;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spinnaker.cats.cache.Cache;
import com.netflix.spinnaker.cats.cache.CacheData;
import com.netflix.spinnaker.clouddriver.ecs.cache.model.Task;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static com.netflix.spinnaker.clouddriver.ecs.cache.Keys.Namespace.TASKS;

@Component
public class TaskCacheClient extends AbstractCacheClient<Task> {
  private ObjectMapper objectMapper;

  @Autowired
  public TaskCacheClient(Cache cacheView, ObjectMapper objectMapper) {
    super(cacheView, TASKS.toString());
    this.objectMapper = objectMapper;
  }

  @Override
  protected Task convert(CacheData cacheData) {
    Task task = new Task();
    Map<String, Object> attributes = cacheData.getAttributes();
    task.setTaskId((String) attributes.get("taskId"));
    task.setTaskArn((String) attributes.get("taskArn"));
    task.setClusterArn((String) attributes.get("clusterArn"));
    task.setContainerInstanceArn((String) attributes.get("containerInstanceArn"));
    task.setGroup((String) attributes.get("group"));
    task.setLastStatus((String) attributes.get("lastStatus"));
    task.setDesiredStatus((String) attributes.get("desiredStatus"));
    if (attributes.containsKey("startedAt")) {
      task.setStartedAt((Long) attributes.get("startedAt"));
    }

    if (attributes.containsKey("containers")) {
      List<Map<String, Object>> containers = (List<Map<String, Object>>) attributes.get("containers");
      List<Container> deserializedLoadbalancers = new ArrayList<>(containers.size());

      for (Map<String, Object> serializedContainer : containers) {
        if (serializedContainer != null) {
          deserializedLoadbalancers.add(objectMapper.convertValue(serializedContainer, Container.class));
        }
      }

      task.setContainers(deserializedLoadbalancers);
    } else {
      task.setContainers(Collections.emptyList());
    }

    return task;
  }
}
