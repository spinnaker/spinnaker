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

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.front50.exception.BadRequestException
import com.netflix.spinnaker.front50.exception.NotFoundException
import com.netflix.spinnaker.front50.exceptions.DuplicateEntityException
import com.netflix.spinnaker.front50.exceptions.InvalidEntityException
import com.netflix.spinnaker.front50.exceptions.InvalidRequestException
import com.netflix.spinnaker.front50.model.pipeline.Pipeline
import com.netflix.spinnaker.front50.model.pipeline.PipelineDAO
import com.netflix.spinnaker.front50.model.pipeline.PipelineTemplateDAO
import com.netflix.spinnaker.front50.model.pipeline.TemplateConfiguration
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.security.access.prepost.PostFilter
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.*

import static com.netflix.spinnaker.front50.model.pipeline.Pipeline.TYPE_TEMPLATED
import static com.netflix.spinnaker.front50.model.pipeline.TemplateConfiguration.TemplateSource.SPINNAKER_PREFIX

/**
 * Controller for presets
 */
@RestController
@RequestMapping('pipelines')
class PipelineController {

  private final Logger log = LoggerFactory.getLogger(getClass())

  @Autowired(required = false)
  PipelineTemplateDAO pipelineTemplateDAO = null;

  PipelineDAO pipelineDAO
  ObjectMapper objectMapper;

  @Autowired
  public PipelineController(PipelineDAO pipelineDAO, ObjectMapper objectMapper) {
    this.pipelineDAO = pipelineDAO
    this.objectMapper = objectMapper
  }

  @PreAuthorize("#restricted ? @fiatPermissionEvaluator.storeWholePermission() : true")
  @PostFilter("#restricted ? hasPermission(filterObject.name, 'APPLICATION', 'READ') : true")
  @RequestMapping(value = '', method = RequestMethod.GET)
  List<Pipeline> list(@RequestParam(required = false, value = 'restricted', defaultValue = 'true') boolean restricted,
                      @RequestParam(required = false, value = 'refresh', defaultValue = 'true') boolean refresh) {
    pipelineDAO.all(refresh)
  }

  @PreAuthorize("hasPermission(#application, 'APPLICATION', 'READ')")
  @RequestMapping(value = '{application:.+}', method = RequestMethod.GET)
  List<Pipeline> listByApplication(@PathVariable(value = 'application') String application,
                                   @RequestParam(required = false, value = 'refresh', defaultValue = 'true') boolean refresh) {
    List<Pipeline> pipelines = pipelineDAO.getPipelinesByApplication(application, refresh)
    pipelines.sort { p1, p2 ->
      if (p1.index != null && p2.index == null) {
        return -1
      }
      if (p1.index == null && p2.index != null) {
        return 1
      }
      if (p1.index != p2.index) {
        return p1.index - p2.index
      }
      return (p1.getName() ?: p1.getId()).compareToIgnoreCase(p2.getName() ?: p2.getId())
    }
    pipelines.eachWithIndex{ Pipeline entry, int i -> entry.index = i }
    return pipelines
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
  Pipeline save(@RequestBody Pipeline pipeline) {

    validatePipeline(pipeline)

    pipeline.name = pipeline.getName().trim()
    pipeline = ensureCronTriggersHaveIdentifier(pipeline)

    if (!pipeline.id) {
      // ensure that cron triggers are assigned a unique identifier for new pipelines
      def triggers = (pipeline.triggers ?: []) as List<Map>
      triggers.findAll { it.type == "cron" }.each { Map trigger ->
        trigger.id = UUID.randomUUID().toString()
      }
    }

    return pipelineDAO.create(pipeline.id as String, pipeline)
  }

  @PreAuthorize("@fiatPermissionEvaluator.isAdmin()")
  @RequestMapping(value = 'batchUpdate', method = RequestMethod.POST)
  void batchUpdate(@RequestBody List<Pipeline> pipelines) {
    pipelineDAO.bulkImport(pipelines)
  }

  @PreAuthorize("hasPermission(#application, 'APPLICATION', 'WRITE')")
  @RequestMapping(value = '{application}/{pipeline:.+}', method = RequestMethod.DELETE)
  void delete(@PathVariable String application, @PathVariable String pipeline) {
    String pipelineId = pipelineDAO.getPipelineId(application, pipeline)
    log.info("Deleting pipeline \"{}\" with id {} in application {}", pipeline, pipelineId, application)
    pipelineDAO.delete(pipelineId)
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

    validatePipeline(pipeline)

    pipeline.name = pipeline.getName().trim()
    pipeline.updateTs = System.currentTimeMillis()
    pipeline = ensureCronTriggersHaveIdentifier(pipeline)

    pipelineDAO.update(id, pipeline)
    return pipeline
  }

  /**
   * Ensure basic validity of the pipeline. Invalid pipelines will raise runtime exceptions.
   *
   *
   * @param pipeline The Pipeline to validate
   */
  private void validatePipeline(Pipeline pipeline) {

    // Pipelines must have an application and a name
    if (!pipeline.application || !pipeline.name) {
      throw new InvalidEntityException("A pipeline requires name and application fields")
    }

    //Check if pipeline type is templated
    if(pipeline.getType() == TYPE_TEMPLATED) {
      PipelineTemplateDAO templateDAO = getTemplateDAO()

      //Check templated pipelines to ensure template is valid
      TemplateConfiguration config = objectMapper.convertValue(pipeline.getConfig(), TemplateConfiguration.class);

      //With the source check if it starts with "spinnaker://"
      //Check if template id which is after :// is in the store
      String source = config.pipeline.template.source
      if (source.startsWith(SPINNAKER_PREFIX)) {
        String templateId = source.substring(SPINNAKER_PREFIX.length())

        try {
          templateDAO.findById(templateId)
        } catch (NotFoundException notFoundEx) {
          throw new BadRequestException("Configured pipeline template not found", notFoundEx)
        }
      }
    }

    checkForDuplicatePipeline(pipeline.getApplication(), pipeline.getName().trim(), pipeline.getId())
  }

  private PipelineTemplateDAO getTemplateDAO() {
    if (pipelineTemplateDAO == null) {
      throw new BadRequestException("Pipeline Templates are not supported with your current storage backend");
    }
    return pipelineTemplateDAO;
  }

  private void checkForDuplicatePipeline(String application, String name, String id = null) {
    if (pipelineDAO.getPipelinesByApplication(application).any {
      it.getName().equalsIgnoreCase(name) &&
      it.getId() != id
    }) {
      throw new DuplicateEntityException("A pipeline with name ${name} already exists in application ${application}")
    }
  }

  private static Pipeline ensureCronTriggersHaveIdentifier(Pipeline pipeline) {
    def triggers = (pipeline.triggers ?: []) as List<Map>
    triggers.findAll { "cron".equalsIgnoreCase(it.type) }.each { Map trigger ->
      // ensure that all cron triggers have an assigned identifier
      trigger.id = trigger.id ?: UUID.randomUUID().toString()
    }

    return pipeline
  }
}
