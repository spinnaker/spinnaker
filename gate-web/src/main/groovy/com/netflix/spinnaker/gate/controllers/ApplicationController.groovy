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
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.web.bind.annotation.*
import org.springframework.web.context.request.async.DeferredResult


import static com.netflix.spinnaker.gate.controllers.AsyncControllerSupport.defer

@CompileStatic
@RequestMapping("/applications")
@RestController
class ApplicationController {

  @Autowired
  ApplicationService applicationService

  @Autowired
  TaskService taskService

  @Autowired
  TagService tagService

  @RequestMapping(method = RequestMethod.GET)
  DeferredResult<List<Map>> get() {
    defer applicationService.all
  }

  @RequestMapping(method = RequestMethod.POST)
  DeferredResult<Map> create(@RequestBody Map<String, String> app) {
    defer applicationService.create(app)
  }

  @RequestMapping(value = "/{name}", method = RequestMethod.GET)
  DeferredResult<Map> show(@PathVariable("name") String name) {
    defer applicationService.get(name)
  }

  @RequestMapping(value = "/{name}", method = RequestMethod.DELETE)
  DeferredResult<Map> delete(@RequestParam String account, @PathVariable String name) {
    defer applicationService.delete(account, name)
  }

  @RequestMapping(value = "/{name}/bake", method = RequestMethod.POST)
  DeferredResult<Map> bake(@PathVariable("name") String name, @RequestBody(required = false) BakeCommand bakeCommand) {
    if (!bakeCommand) {
      bakeCommand = new BakeCommand(pkg: name)
    }
    if (!bakeCommand.pkg) {
      bakeCommand.pkg = name
    }
    defer applicationService.bake(name, bakeCommand.pkg, bakeCommand.baseOs, bakeCommand.baseLabel, bakeCommand.region)
  }

  @RequestMapping(value = "/{name}/tasks", method = RequestMethod.GET)
  DeferredResult<List> getTasks(@PathVariable("name") String name) {
    defer applicationService.getTasks(name)
  }

  @RequestMapping(value = "/{name}/pipelines", method = RequestMethod.GET)
  DeferredResult<List> getPiplines(@PathVariable("name") String name) {
    defer applicationService.getPipelines(name)
  }

  @RequestMapping(value = "/{name}/pipelineConfigs", method = RequestMethod.GET)
  DeferredResult<List> getPiplineConfigs(@PathVariable("name") String name) {
    defer applicationService.getPipelineConfigs(name)
  }

  @RequestMapping(value = "/{name}/pipelineConfigs/{pipelineName}", method = RequestMethod.GET)
  DeferredResult<List> getPiplineConfig(
      @PathVariable("name") String name, @PathVariable("pipelineName") String pipelineName) {
    defer applicationService.getPipelineConfig(name, pipelineName)
  }

  @RequestMapping(value = "/{name}/tasks/{id}", method = RequestMethod.GET)
  DeferredResult<Map> getTask(@PathVariable("id") String id) {
    defer taskService.getTask(id)
  }

  @RequestMapping(value = "/{name}/tasks", method = RequestMethod.POST)
  DeferredResult<Map> task(@RequestBody Map map) {
    defer taskService.create(map)
  }

  @RequestMapping(value = "/{name}/tags", method = RequestMethod.GET)
  DeferredResult<List<String>> getTags(@PathVariable("name") String name) {
    defer tagService.getTags(name)
  }

  static class BakeCommand {
    String pkg
    String baseOs = "ubuntu"
    String baseLabel = "release"
    String region = "us-east-1"
  }

}
