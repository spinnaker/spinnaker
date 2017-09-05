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
package com.netflix.spinnaker.front50.model.pipeline;

import com.netflix.spectator.api.Registry;
import com.netflix.spinnaker.front50.model.ObjectKeyLoader;
import com.netflix.spinnaker.front50.model.ObjectType;
import com.netflix.spinnaker.front50.model.StorageService;
import com.netflix.spinnaker.front50.model.StorageServiceSupport;
import org.springframework.util.Assert;
import rx.Scheduler;

import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

public class DefaultPipelineTemplateDAO extends StorageServiceSupport<PipelineTemplate> implements PipelineTemplateDAO {

  public DefaultPipelineTemplateDAO(StorageService service,
                                    Scheduler scheduler,
                                    ObjectKeyLoader objectKeyLoader,
                                    long refreshIntervalMs,
                                    boolean shouldWarmCache,
                                    Registry registry) {
    super(ObjectType.PIPELINE_TEMPLATE, service, scheduler, objectKeyLoader, refreshIntervalMs, shouldWarmCache, registry);
  }

  @Override
  public Collection<PipelineTemplate> getPipelineTemplatesByScope(List<String> scope) {
    return all()
      .stream()
      .filter(pt -> pt.containsAnyScope(scope))
      .collect(Collectors.toList());
  }

  @Override
  public PipelineTemplate create(String id, PipelineTemplate item) {
    Assert.notNull(item.getId(), "id field must NOT to be null!");
    Assert.notEmpty(item.getScopes(), "scopes field must have at least ONE scope!");

    update(id, item);
    return findById(id);
  }
}
