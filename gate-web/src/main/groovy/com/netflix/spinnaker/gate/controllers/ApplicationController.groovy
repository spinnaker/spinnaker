/*
 * Copyright 2014 Netflix, Inc.
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

import com.netflix.spinnaker.gate.services.*
import groovy.transform.CompileStatic
import groovy.transform.InheritConstructors
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@CompileStatic
@RequestMapping("/applications")
@RestController
class ApplicationController {

  @Autowired
  ApplicationService applicationService

  @Autowired
  TaskService taskService

  @Autowired(required = false)
  TagService tagService

  @Autowired(required = false)
  PipelineService pipelineService

  @RequestMapping(method = RequestMethod.GET)
  List<Map> all() {
    applicationService.all
  }

  @RequestMapping(method = RequestMethod.POST)
  Map create(@RequestBody Map<String, String> app) {
    applicationService.create(app)
  }

  @RequestMapping(value = "/{name:.+}", method = RequestMethod.GET)
  Map show(@PathVariable("name") String name) {
    def result = applicationService.get(name)
    if (!result) {
      throw new ApplicationNotFoundException("Application ${name} not found")
    } else if (!result.name) {
      // applicationService.get() doesn't set the name unless clusters are found. Deck requires the name.
      result.name = name
    }
    result
  }

  @RequestMapping(value = "/{name:.+}", method = RequestMethod.DELETE)
  Map delete(@RequestParam String account, @PathVariable String name) {
    applicationService.delete(account, name)
  }

  @RequestMapping(value = "/{name}/bake", method = RequestMethod.POST)
  Map bake(@PathVariable("name") String name, @RequestBody(required = false) BakeCommand bakeCommand) {
    if (!bakeCommand) {
      bakeCommand = new BakeCommand(pkg: name)
    }
    if (!bakeCommand.pkg) {
      bakeCommand.pkg = name
    }
    applicationService.bake(name, bakeCommand.pkg, bakeCommand.baseOs, bakeCommand.baseLabel, bakeCommand.region)
  }

  @RequestMapping(value = "/{name}/tasks", method = RequestMethod.GET)
  List getTasks(@PathVariable("name") String name) {
    applicationService.getTasks(name)
  }

  @RequestMapping(value = "/{name}/pipelines", method = RequestMethod.GET)
  List getPipelines(@PathVariable("name") String name) {
    applicationService.getPipelines(name)
  }

  @RequestMapping(value = "/{name}/pipelines/{id}/cancel", method = RequestMethod.PUT)
  Map cancelPipeline(@PathVariable("id") String id) {
    taskService.cancelPipeline(id)
  }

  @RequestMapping(value = "/{name}/pipelineConfigs", method = RequestMethod.GET)
  List getPipelineConfigs(@PathVariable("name") String name) {
    applicationService.getPipelineConfigs(name)
  }

  @RequestMapping(value = "/{name}/pipelineConfigs/{pipelineName}", method = RequestMethod.GET)
  Map getPipelineConfig(
      @PathVariable("name") String name, @PathVariable("pipelineName") String pipelineName) {
    applicationService.getPipelineConfigs(name).find {
      it.name == pipelineName
    }
  }

  @RequestMapping(value = "/{name}/pipelineConfigs/{pipelineName}", method = RequestMethod.POST, params = ['user'])
  HttpEntity invokePipelineConfig(@PathVariable("name") String application,
                                  @PathVariable("pipelineName") String pipelineName,
                                  @RequestParam("user") String user) {
    try {
      pipelineService.trigger(application, pipelineName, user)
      new ResponseEntity(HttpStatus.ACCEPTED)
    } catch (e) {
      new ResponseEntity([message: e.message], new HttpHeaders(), HttpStatus.UNPROCESSABLE_ENTITY)
    }
  }

  @RequestMapping(value = "/{name}/tasks/{id}", method = RequestMethod.GET)
  Map getTask(@PathVariable("id") String id) {
    taskService.getTask(id)
  }

  @RequestMapping(value = "/{name}/tasks/{id}/cancel", method = RequestMethod.PUT)
  Map cancelTask(@PathVariable("id") String id) {
    taskService.cancelTask(id)
  }

  @RequestMapping(value = "/{name}/tasks/{id}/details/{taskDetailsId}", method = RequestMethod.GET)
  Map getTaskDetails(@PathVariable("id") String id, @PathVariable("taskDetailsId") String taskDetailsId) {
    taskService.getTaskDetails(taskDetailsId)
  }

  @RequestMapping(value = "/{name}/tasks", method = RequestMethod.POST)
  Map task(@RequestBody Map map) {
    taskService.create(map)
  }

  @RequestMapping(value = "/{name}/tags", method = RequestMethod.GET)
  List<String> getTags(@PathVariable("name") String name) {
    if (tagService) {
      tagService.getTags(name)
    } else {
      []
    }
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
