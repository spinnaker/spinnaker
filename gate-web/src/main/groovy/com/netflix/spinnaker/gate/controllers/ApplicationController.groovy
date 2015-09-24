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
import com.netflix.spinnaker.gate.services.PipelineService
import com.netflix.spinnaker.gate.services.TaskService
import groovy.transform.CompileStatic
import groovy.transform.InheritConstructors
import groovy.util.logging.Slf4j
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@CompileStatic
@RequestMapping("/applications")
@RestController
@Slf4j
class ApplicationController {

  @Autowired
  ApplicationService applicationService

  @Autowired
  ExecutionHistoryService executionHistoryService

  @Autowired
  TaskService taskService

  @Autowired(required = false)
  PipelineService pipelineService

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
  List getTasks(@PathVariable("application") String application) {
    executionHistoryService.getTasks(application)
  }

  @RequestMapping(value = "/{application}/pipelines", method = RequestMethod.GET)
  List getPipelines(@PathVariable("application") String application) {
    executionHistoryService.getPipelines(application)
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

  /**
   * @deprecated  Use PipelineController instead for pipeline operations.
   */
  @Deprecated
  @RequestMapping(value = "/{application}/pipelineConfigs/{pipelineName:.+}", method = RequestMethod.POST)
  HttpEntity invokePipelineConfig(@PathVariable("application") String application,
                                  @PathVariable("pipelineName") String pipelineName,
                                  @RequestBody(required = false) Map trigger,
                                  @RequestParam(required = false, value = "user") String user) {
    //TODO(cfieber) - remove the request param and make the body required once this is rolled all the way
    if (trigger == null) {
      trigger = [:]
    }

    if (!trigger.user) {
      trigger.user = (user ?: 'anonymous')
    }

    try {
      def body = pipelineService.trigger(application, pipelineName, trigger)
      new ResponseEntity(body, HttpStatus.ACCEPTED)
    } catch (e) {
      new ResponseEntity([message: e.message], new HttpHeaders(), HttpStatus.UNPROCESSABLE_ENTITY)
    }
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
