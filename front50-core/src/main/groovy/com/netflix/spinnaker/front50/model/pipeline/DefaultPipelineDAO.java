/*
 * Copyright 2016 Netflix, Inc.
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

package com.netflix.spinnaker.front50.model.pipeline;

import com.netflix.spectator.api.Registry;
import com.netflix.spinnaker.front50.exception.NotFoundException;
import com.netflix.spinnaker.front50.model.ObjectKeyLoader;
import com.netflix.spinnaker.front50.model.ObjectType;
import com.netflix.spinnaker.front50.model.StorageService;
import com.netflix.spinnaker.front50.model.StorageServiceSupport;
import org.springframework.util.Assert;
import rx.Scheduler;

import java.util.Collection;
import java.util.UUID;
import java.util.stream.Collectors;

public class DefaultPipelineDAO extends StorageServiceSupport<Pipeline> implements PipelineDAO {
  public DefaultPipelineDAO(StorageService service,
                            Scheduler scheduler,
                            ObjectKeyLoader objectKeyLoader,
                            long refreshIntervalMs,
                            boolean shouldWarmCache,
                            Registry registry) {
    super(ObjectType.PIPELINE, service, scheduler, objectKeyLoader, refreshIntervalMs, shouldWarmCache, registry);
  }

  @Override
  public String getPipelineId(String application, String pipelineName) {
    Pipeline matched = getPipelinesByApplication(application, true)
      .stream()
      .filter(pipeline -> pipeline.getName().equalsIgnoreCase(pipelineName))
      .findFirst()
      .orElseThrow(() -> new NotFoundException(
        String.format("No pipeline found with name '%s' in application '%s'", pipelineName, application)
      ));

    return matched.getId();
  }

  @Override
  public Collection<Pipeline> getPipelinesByApplication(String application) {
    return getPipelinesByApplication(application, true);
  }

  @Override
  public Collection<Pipeline> getPipelinesByApplication(String application, boolean refresh) {
    return all(refresh)
      .stream()
      .filter(pipeline -> pipeline.getApplication() != null && pipeline.getApplication().equalsIgnoreCase(application))
      .collect(Collectors.toList());
  }

  @Override
  public Pipeline create(String id, Pipeline item) {
    if (id == null) {
      id = UUID.randomUUID().toString();
    }
    item.setId(id);

    Assert.notNull(item.getApplication(), "application field must NOT be null!");
    Assert.notNull(item.getName(), "name field must NOT be null!");

    update(id, item);
    return findById(id);
  }
}
