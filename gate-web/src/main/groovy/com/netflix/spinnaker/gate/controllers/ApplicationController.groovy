/*
 * Copyright 2015 Netflix, Inc.
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

package com.netflix.spinnaker.gate.controllers

import com.netflix.spinnaker.gate.services.ApplicationService
import com.netflix.spinnaker.gate.services.ExecutionHistoryService
import com.netflix.spinnaker.gate.services.TaskService
import groovy.transform.CompileStatic
import groovy.transform.InheritConstructors
import groovy.util.logging.Slf4j
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.core.env.Environment
import org.springframework.http.HttpEntity
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.*

@CompileStatic
@RequestMapping("/applications")
@RestController
@Slf4j
class ApplicationController {

  static final String PIPELINE_EXECUTION_LIMIT = 'gate.execution.fetch.limit'

  @Autowired
  ApplicationService applicationService

  @Autowired
  ExecutionHistoryService executionHistoryService

  @Autowired
  TaskService taskService

  @Autowired
  PipelineController pipelineController

  @Autowired
  Environment environment

  @RequestMapping(method = RequestMethod.GET)
  List<Map> all() {
    applicationService.all
  }

  @RequestMapping(value = "/{application:.+}", method = RequestMethod.GET)
  Map show(@PathVariable("application") String application) {
    def result = applicationService.get(application)
    if (!result) {
      log.warn("Application ${application} not found")
      throw new ApplicationNotFoundException("Application ${application} not found")
    } else if (!result.name) {
      // applicationService.get() doesn't set the name unless clusters are found. Deck requires the name.
      result.name = application
    }
    result
  }

  @RequestMapping(value = "/{application}/tasks", method = RequestMethod.GET)
  List getTasks(@PathVariable("application") String application,
                @RequestParam(value = "limit", required = false) Integer limit,
                @RequestParam(value = "statuses", required = false) String statuses) {
    executionHistoryService.getTasks(application, limit, statuses)
  }

  @RequestMapping(value = "/{application}/pipelines", method = RequestMethod.GET)
  List getPipelines(@PathVariable("application") String application,
                    @RequestParam(value = "limit", required = false) Integer limit,
                    @RequestParam(value = "statuses", required = false) String statuses) {
    def listLimit = limit ?: environment.getProperty(PIPELINE_EXECUTION_LIMIT, Integer, 10)
    log.info("execution fetch limit: ${listLimit}")
    executionHistoryService.getPipelines(application, listLimit, statuses)
  }

  /**
   * @deprecated  There is no reason to provide an app name, use PipelineController instead for pipeline operations.
   */
  @Deprecated
  @RequestMapping(value = "/{application}/pipelines/{id}/cancel", method = RequestMethod.PUT)
  Map cancelPipeline(@PathVariable("id") String id) {
    taskService.cancelPipeline(id)
  }

  @RequestMapping(value = "/{application}/pipelineConfigs", method = RequestMethod.GET)
  List getPipelineConfigs(@PathVariable("application") String application) {
    applicationService.getPipelineConfigs(application)
  }

  @RequestMapping(value = "/{application}/pipelineConfigs/{pipelineName:.+}", method = RequestMethod.GET)
  Map getPipelineConfig(
      @PathVariable("application") String application, @PathVariable("pipelineName") String pipelineName) {
    applicationService.getPipelineConfigs(application).find {
      it.name == pipelineName
    }
  }

  @RequestMapping(value = "/{application}/strategyConfigs", method = RequestMethod.GET)
  List getStrategyConfigs(@PathVariable("application") String application) {
    applicationService.getStrategyConfigs(application)
  }

  @RequestMapping(value = "/{application}/strategyConfigs/{strategyName:.+}", method = RequestMethod.GET)
  Map getStrategyConfig(
    @PathVariable("application") String application, @PathVariable("strategyName") String strategyName) {
    applicationService.getStrategyConfigs(application).find {
      it.name == strategyName
    }
  }

  /**
   * @deprecated  Use PipelineController instead for pipeline operations.
   */
  @Deprecated
  @RequestMapping(value = "/{application}/pipelineConfigs/{pipelineName:.+}", method = RequestMethod.POST)
  HttpEntity invokePipelineConfig(@PathVariable("application") String application,
                                  @PathVariable("pipelineName") String pipelineName,
                                  @RequestBody(required = false) Map trigger,
                                  @RequestParam(required = false, value = "user") String user) {
    return pipelineController.invokePipelineConfig(application, pipelineName, trigger)
  }

  /**
   * @deprecated  There is no reason to provide an app name, use TaskController instead for task operations.
   */
  @Deprecated
  @RequestMapping(value = "/{application}/tasks/{id}", method = RequestMethod.GET)
  Map getTask(@PathVariable("id") String id) {
    taskService.getTask(id)
  }

  /**
   * @deprecated  There is no reason to provide an app name, use TaskController instead for task operations.
   */
  @Deprecated
  @RequestMapping(value = "/{application}/tasks/{id}/cancel", method = RequestMethod.PUT)
  Map cancelTask(@PathVariable("id") String id) {
    taskService.cancelTask(id)
  }

  /**
   * @deprecated  There is no reason to provide an app name, use TaskController instead for task operations.
   */
  @Deprecated
  @RequestMapping(value = "/{application}/tasks/{id}/details/{taskDetailsId}", method = RequestMethod.GET)
  Map getTaskDetails(@PathVariable("id") String id, @PathVariable("taskDetailsId") String taskDetailsId) {
    taskService.getTaskDetails(taskDetailsId)
  }

  /**
   * @deprecated  There is no reason to provide an app name, use TaskController instead for task operations.
   */
  @Deprecated
  @RequestMapping(value = "/{application}/tasks", method = RequestMethod.POST)
  Map task(@PathVariable String application, @RequestBody Map map) {
    taskService.createAppTask(application, map)
  }

  static class BakeCommand {
    String pkg
    String baseOs = "ubuntu"
    String baseLabel = "release"
    String region = "us-east-1"
  }

  @ResponseStatus(HttpStatus.NOT_FOUND)
  @InheritConstructors
  static class ApplicationNotFoundException extends RuntimeException {}
}
