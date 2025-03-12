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

import static com.netflix.spinnaker.clouddriver.core.provider.agent.Namespace.HEALTH;

import com.netflix.spinnaker.cats.cache.Cache;
import com.netflix.spinnaker.cats.cache.CacheData;
import com.netflix.spinnaker.clouddriver.ecs.cache.model.TaskHealth;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class TaskHealthCacheClient extends AbstractCacheClient<TaskHealth> {

  @Autowired
  public TaskHealthCacheClient(Cache cacheView) {
    super(cacheView, HEALTH.toString());
  }

  @Override
  protected TaskHealth convert(CacheData cacheData) {
    TaskHealth taskHealth = new TaskHealth();

    Map<String, Object> attributes = cacheData.getAttributes();
    taskHealth.setState((String) attributes.get("state"));
    taskHealth.setType((String) attributes.get("type"));
    taskHealth.setServiceName((String) attributes.get("service"));
    taskHealth.setTaskArn((String) attributes.get("taskArn"));
    taskHealth.setInstanceId((String) attributes.get("instanceId"));
    taskHealth.setTaskId((String) attributes.get("taskId"));

    return taskHealth;
  }
}
