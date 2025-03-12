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
import com.netflix.spinnaker.front50.api.model.pipeline.Pipeline;
import com.netflix.spinnaker.front50.config.StorageServiceConfigurationProperties;
import com.netflix.spinnaker.front50.model.ObjectKeyLoader;
import com.netflix.spinnaker.front50.model.ObjectType;
import com.netflix.spinnaker.front50.model.StorageService;
import com.netflix.spinnaker.front50.model.StorageServiceSupport;
import com.netflix.spinnaker.kork.web.exceptions.NotFoundException;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import java.util.Collection;
import java.util.UUID;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.Assert;
import org.springframework.util.ObjectUtils;
import rx.Scheduler;

public class DefaultPipelineDAO extends StorageServiceSupport<Pipeline> implements PipelineDAO {
  private static final Logger log = LoggerFactory.getLogger(DefaultPipelineDAO.class);

  public DefaultPipelineDAO(
      StorageService service,
      Scheduler scheduler,
      ObjectKeyLoader objectKeyLoader,
      StorageServiceConfigurationProperties.PerObjectType configurationProperties,
      Registry registry,
      CircuitBreakerRegistry circuitBreakerRegistry) {
    super(
        ObjectType.PIPELINE,
        service,
        scheduler,
        objectKeyLoader,
        configurationProperties,
        registry,
        circuitBreakerRegistry);
  }

  @Override
  public String getPipelineId(String application, String pipelineName) {
    Pipeline matched =
        getPipelinesByApplication(application, true).stream()
            .filter(pipeline -> pipeline.getName().equalsIgnoreCase(pipelineName))
            .findFirst()
            .orElseThrow(
                () ->
                    new NotFoundException(
                        String.format(
                            "No pipeline found with name '%s' in application '%s'",
                            pipelineName, application)));

    return matched.getId();
  }

  @Override
  public Collection<Pipeline> getPipelinesByApplication(String application) {
    return getPipelinesByApplication(application, true);
  }

  @Override
  public Collection<Pipeline> getPipelinesByApplication(String application, boolean refresh) {
    return getPipelinesByApplication(application, null, refresh);
  }

  @Override
  public Collection<Pipeline> getPipelinesByApplication(
      String application, String pipelineNameFilter, boolean refresh) {
    return all(refresh).stream()
        .filter(
            pipeline -> {
              /*
              There's a sneaky bug where some application names are null. It's hard to find,
              so for debugging purposes, we ALWAYS want to check for null pipeline names.
               */
              if (pipeline.getName() == null) {
                log.error(
                    "Pipeline with (id={}, app={}, type={}, lastModified={}) does not have a name.",
                    pipeline.getId(),
                    pipeline.getApplication(),
                    pipeline.getType(),
                    pipeline.getLastModified());
              }

              if (pipeline.getApplication() == null
                  || !pipeline.getApplication().equalsIgnoreCase(application)) {
                return false;
              }

              /*
              if the pipeline name filter is empty, we want to treat it as if it doesn't exist.
              if isEmpty returns true, the statement will short circuit and return true,
              which effectively means we don't use the filter at all.
              */
              return ObjectUtils.isEmpty(pipelineNameFilter)
                  || pipeline.getName() != null
                      && pipeline
                          .getName()
                          .toLowerCase()
                          .contains(pipelineNameFilter.toLowerCase());
            })
        .collect(Collectors.toList());
  }

  @Override
  public Pipeline getPipelineByName(String application, String pipelineName, boolean refresh) {
    return all(refresh).stream()
        .filter(
            pipeline ->
                pipeline.getApplication() != null
                    && pipeline.getApplication().equalsIgnoreCase(application)
                    && pipeline.getName() != null
                    && pipeline.getName().equalsIgnoreCase(pipelineName))
        .findFirst()
        .orElseThrow(
            () ->
                new NotFoundException(
                    String.format(
                        "No pipeline found in cache with application %s, name %s",
                        application, pipelineName)));
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
