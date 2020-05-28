/*
 * Copyright 2014 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.netflix.spinnaker.front50.controllers;

import static java.lang.String.format;

import com.google.common.base.Strings;
import com.netflix.spinnaker.front50.exceptions.DuplicateEntityException;
import com.netflix.spinnaker.front50.exceptions.InvalidRequestException;
import com.netflix.spinnaker.front50.model.pipeline.Pipeline;
import com.netflix.spinnaker.front50.model.pipeline.PipelineStrategyDAO;
import java.util.*;
import org.springframework.security.access.prepost.PostFilter;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/** Controller for presets */
@RestController
@RequestMapping("strategies")
public class StrategyController {

  private final PipelineStrategyDAO pipelineStrategyDAO;

  public StrategyController(PipelineStrategyDAO pipelineStrategyDAO) {
    this.pipelineStrategyDAO = pipelineStrategyDAO;
  }

  @PreAuthorize("@fiatPermissionEvaluator.storeWholePermission()")
  @PostFilter("hasPermission(filterObject.application, 'APPLICATION', 'READ')")
  @RequestMapping(value = "", method = RequestMethod.GET)
  public Collection<Pipeline> list() {
    return pipelineStrategyDAO.all();
  }

  @PreAuthorize("hasPermission(#application, 'APPLICATION', 'READ')")
  @RequestMapping(value = "{application:.+}", method = RequestMethod.GET)
  public Collection<Pipeline> listByApplication(
      @PathVariable(value = "application") String application) {
    return pipelineStrategyDAO.getPipelinesByApplication(application);
  }

  @PostFilter("hasPermission(filterObject.application, 'APPLICATION', 'READ')")
  @RequestMapping(value = "{id:.+}/history", method = RequestMethod.GET)
  public Collection<Pipeline> getHistory(
      @PathVariable String id, @RequestParam(value = "limit", defaultValue = "20") int limit) {
    return pipelineStrategyDAO.history(id, limit);
  }

  @PreAuthorize("hasPermission(#strategy.application, 'APPLICATION', 'WRITE')")
  @RequestMapping(value = "", method = RequestMethod.POST)
  public void save(@RequestBody Pipeline strategy) {
    if (Strings.isNullOrEmpty(strategy.getId())) {
      checkForDuplicatePipeline(strategy.getApplication(), strategy.getName());

      // ensure that cron triggers are assigned a unique identifier for new strategies
      if (strategy.getTriggers() != null) {
        strategy.getTriggers().stream()
            .filter(it -> "cron".equals(it.getType()))
            .forEach(it -> it.put("id", UUID.randomUUID().toString()));
      }
    }

    pipelineStrategyDAO.create(strategy.getId(), strategy);
  }

  @PreAuthorize("@fiatPermissionEvaluator.isAdmin()")
  @RequestMapping(value = "batchUpdate", method = RequestMethod.POST)
  public void batchUpdate(@RequestBody List<Pipeline> strategies) {
    pipelineStrategyDAO.bulkImport(strategies);
  }

  @PreAuthorize("hasPermission(#application, 'APPLICATION', 'WRITE')")
  @RequestMapping(value = "{application}/{strategy:.+}", method = RequestMethod.DELETE)
  public void delete(@PathVariable String application, @PathVariable String strategy) {
    pipelineStrategyDAO.delete(pipelineStrategyDAO.getPipelineId(application, strategy));
  }

  public void delete(@PathVariable String id) {
    pipelineStrategyDAO.delete(id);
  }

  @PreAuthorize("hasPermission(#strategy.application, 'APPLICATION', 'WRITE')")
  @RequestMapping(value = "/{id}", method = RequestMethod.PUT)
  public Pipeline update(@PathVariable final String id, @RequestBody final Pipeline strategy) {
    Pipeline existingStrategy = pipelineStrategyDAO.findById(id);
    if (!strategy.getId().equals(existingStrategy.getId())) {
      throw new InvalidRequestException(
          format("The provided id '%s' doesn't match the strategy id '%s'", id, strategy.getId()));
    }
    boolean alreadyExists =
        pipelineStrategyDAO.getPipelinesByApplication(strategy.getApplication()).stream()
            .anyMatch(
                it -> it.getName().equalsIgnoreCase(strategy.getName()) && !it.getId().equals(id));
    if (alreadyExists) {
      throw new DuplicateEntityException(
          format(
              "A strategy with name '%s' already exists in application '%s'",
              strategy.getName(), strategy.getApplication()));
    }

    strategy.put("updateTs", System.currentTimeMillis());
    pipelineStrategyDAO.update(id, strategy);

    return strategy;
  }

  private void checkForDuplicatePipeline(final String application, final String name) {
    boolean duplicate =
        pipelineStrategyDAO.getPipelinesByApplication(application).stream()
            .anyMatch(it -> it.getName().equals(name));
    if (duplicate) {
      throw new DuplicateEntityException(
          "A strategy with name " + name + " already exists in application " + application);
    }
  }
}
