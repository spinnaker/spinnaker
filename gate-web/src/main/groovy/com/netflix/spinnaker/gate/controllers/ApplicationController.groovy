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
  TaskService taskService

  @Autowired(required = false)
  PipelineService pipelineService

  @RequestMapping(method = RequestMethod.GET)
  List<Map> all() {
    applicationService.all
  }

  @RequestMapping(value = "/{name:.+}", method = RequestMethod.GET)
  Map show(@PathVariable("name") String name) {
    def result = applicationService.get(name)
    if (!result) {
      log.warn("Application ${name} not found")
      throw new ApplicationNotFoundException("Application ${name} not found")
    } else if (!result.name) {
      // applicationService.get() doesn't set the name unless clusters are found. Deck requires the name.
      result.name = name
    }
    result
  }

  @RequestMapping(value = "/{name}/tasks", method = RequestMethod.GET)
  List getTasks(@PathVariable("name") String name) {
    applicationService.getTasks(name)
  }

  @RequestMapping(value = "/{name}/pipelines", method = RequestMethod.GET)
  List getPipelines(@PathVariable("name") String name) {
    applicationService.getPipelines(name)
  }

  /**
   * @deprecated  There is no reason to provide an app name, use PipelineController instead for pipeline operations.
   */
  @Deprecated
  @RequestMapping(value = "/{name}/pipelines/{id}/cancel", method = RequestMethod.PUT)
  Map cancelPipeline(@PathVariable("id") String id) {
    taskService.cancelPipeline(id)
  }

  @RequestMapping(value = "/{name}/pipelineConfigs", method = RequestMethod.GET)
  List getPipelineConfigs(@PathVariable("name") String name) {
    applicationService.getPipelineConfigs(name)
  }

  @RequestMapping(value = "/{name}/pipelineConfigs/{pipelineName:.+}", method = RequestMethod.GET)
  Map getPipelineConfig(
      @PathVariable("name") String name, @PathVariable("pipelineName") String pipelineName) {
    applicationService.getPipelineConfigs(name).find {
      it.name == pipelineName
    }
  }

  /**
   * @deprecated  Use PipelineController instead for pipeline operations.
   */
  @Deprecated
  @RequestMapping(value = "/{name}/pipelineConfigs/{pipelineName:.+}", method = RequestMethod.POST)
  HttpEntity invokePipelineConfig(@PathVariable("name") String application,
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
  @RequestMapping(value = "/{name}/tasks/{id}", method = RequestMethod.GET)
  Map getTask(@PathVariable("id") String id) {
    taskService.getTask(id)
  }

  /**
   * @deprecated  There is no reason to provide an app name, use TaskController instead for task operations.
   */
  @Deprecated
  @RequestMapping(value = "/{name}/tasks/{id}/cancel", method = RequestMethod.PUT)
  Map cancelTask(@PathVariable("id") String id) {
    taskService.cancelTask(id)
  }

  /**
   * @deprecated  There is no reason to provide an app name, use TaskController instead for task operations.
   */
  @Deprecated
  @RequestMapping(value = "/{name}/tasks/{id}/details/{taskDetailsId}", method = RequestMethod.GET)
  Map getTaskDetails(@PathVariable("id") String id, @PathVariable("taskDetailsId") String taskDetailsId) {
    taskService.getTaskDetails(taskDetailsId)
  }

  /**
   * @deprecated  There is no reason to provide an app name, use TaskController instead for task operations.
   */
  @Deprecated
  @RequestMapping(value = "/{name}/tasks", method = RequestMethod.POST)
  Map task(@PathVariable String name, @RequestBody Map map) {
    taskService.createAppTask(name, map)
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
