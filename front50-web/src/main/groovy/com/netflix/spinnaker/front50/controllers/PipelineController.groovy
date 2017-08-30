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

package com.netflix.spinnaker.front50.controllers

import com.netflix.spinnaker.front50.exceptions.DuplicateEntityException
import com.netflix.spinnaker.front50.exceptions.InvalidEntityException
import com.netflix.spinnaker.front50.exceptions.InvalidRequestException
import com.netflix.spinnaker.front50.model.pipeline.Pipeline
import com.netflix.spinnaker.front50.model.pipeline.PipelineDAO

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.security.access.prepost.PostFilter
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestMethod
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

/**
 * Controller for presets
 */
@RestController
@RequestMapping('pipelines')
class PipelineController {

  @Autowired
  PipelineDAO pipelineDAO

  @PreAuthorize("#restricted ? @fiatPermissionEvaluator.storeWholePermission() : true")
  @PostFilter("#restricted ? hasPermission(filterObject.name, 'APPLICATION', 'READ') : true")
  @RequestMapping(value = '', method = RequestMethod.GET)
  List<Pipeline> list(@RequestParam(required = false, value = 'restricted', defaultValue = 'true') boolean restricted,
                      @RequestParam(required = false, value = 'refresh', defaultValue = 'false') boolean refresh) {
    pipelineDAO.all(refresh)
  }

  @PreAuthorize("hasPermission(#application, 'APPLICATION', 'READ')")
  @RequestMapping(value = '{application:.+}', method = RequestMethod.GET)
  List<Pipeline> listByApplication(@PathVariable(value = 'application') String application,
                                   @RequestParam(required = false, value = 'refresh', defaultValue = 'false') boolean refresh) {
    pipelineDAO.getPipelinesByApplication(application, refresh)
  }

  @PreAuthorize("@fiatPermissionEvaluator.storeWholePermission()")
  @PostFilter("hasPermission(filterObject.application, 'APPLICATION', 'READ')")
  @RequestMapping(value = '{id:.+}/history', method = RequestMethod.GET)
  Collection<Pipeline> getHistory(@PathVariable String id,
                                  @RequestParam(value = "limit", defaultValue = "20") int limit) {
    return pipelineDAO.history(id, limit)
  }

  @PreAuthorize("@fiatPermissionEvaluator.storeWholePermission() and hasPermission(#pipeline.application, 'APPLICATION', 'WRITE') and @authorizationSupport.hasRunAsUserPermission(#pipeline)")
  @RequestMapping(value = '', method = RequestMethod.POST)
  void save(@RequestBody Pipeline pipeline) {
    if (!pipeline.application || !pipeline.name) {
      throw new InvalidEntityException("A pipeline requires name and application fields")
    }

    if (!pipeline.id) {
      checkForDuplicatePipeline(pipeline.getApplication(), pipeline.getName())
      // ensure that cron triggers are assigned a unique identifier for new pipelines
      def triggers = (pipeline.triggers ?: []) as List<Map>
      triggers.findAll { it.type == "cron" }.each { Map trigger ->
        trigger.id = UUID.randomUUID().toString()
      }
    }

    pipelineDAO.create(pipeline.id as String, pipeline)
  }

  @PreAuthorize("@fiatPermissionEvaluator.isAdmin()")
  @RequestMapping(value = 'batchUpdate', method = RequestMethod.POST)
  void batchUpdate(@RequestBody List<Pipeline> pipelines) {
    pipelineDAO.bulkImport(pipelines)
  }

  @PreAuthorize("hasPermission(#application, 'APPLICATION', 'WRITE')")
  @RequestMapping(value = '{application}/{pipeline:.+}', method = RequestMethod.DELETE)
  void delete(@PathVariable String application, @PathVariable String pipeline) {
    pipelineDAO.delete(
      pipelineDAO.getPipelineId(application, pipeline)
    )
  }

  void delete(@PathVariable String id) {
    pipelineDAO.delete(id)
  }

  @PreAuthorize("hasPermission(#pipeline.application, 'APPLICATION', 'WRITE')")
  @RequestMapping(value = "/{id}", method = RequestMethod.PUT)
  Pipeline update(@PathVariable String id, @RequestBody Pipeline pipeline) {
    Pipeline existingPipeline = pipelineDAO.findById(id)
    if (pipeline.id != existingPipeline.id) {
      throw new InvalidRequestException("The provided id ${id} doesn't match the pipeline id ${pipeline.id}")
    }

    if (pipelineDAO.getPipelinesByApplication(pipeline.getApplication()).any {
      it.getName().equalsIgnoreCase(pipeline.getName()) && it.getId() != id }) {
      throw new DuplicateEntityException("A pipeline with name ${pipeline.getName()} already exists in application ${pipeline.application}")
    }

    pipeline.updateTs = System.currentTimeMillis()
    pipelineDAO.update(id, pipeline)
    return pipeline
  }

  private void checkForDuplicatePipeline(String application, String name) {
    if (pipelineDAO.getPipelinesByApplication(application).any {
      it.getName().equalsIgnoreCase(name)
    }) {
      throw new DuplicateEntityException("A pipeline with name ${name} already exists in application ${application}")
    }
  }
}
