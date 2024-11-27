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

import com.netflix.spinnaker.gate.services.TaskService
import groovy.transform.CompileStatic
import io.swagger.v3.oas.annotations.Operation
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.web.bind.annotation.*

@CompileStatic
@RequestMapping("/tasks")
@RestController
class TaskController {

  @Autowired
  TaskService taskService

  @Operation(summary = "Get task")
  @RequestMapping(value = "/{id}", method = RequestMethod.GET)
  Map getTask(@PathVariable("id") String id) {
    taskService.getTask(id)
  }

  @Operation(summary = "Delete task")
  @RequestMapping(value = "/{id}", method = RequestMethod.DELETE)
  Map deleteTask(@PathVariable("id") String id) {
    taskService.deleteTask(id)
  }

  @Operation(summary = "Create task")
  @RequestMapping(method = RequestMethod.POST)
  Map task(@RequestBody Map map) {
    taskService.createAppTask(map)
  }

  @Operation(summary = "Cancel task")
  @RequestMapping(value = "/{id}/cancel", method = RequestMethod.PUT)
  Map cancelTask(@PathVariable("id") String id) {
    taskService.cancelTask(id)
  }

  @Operation(summary = "Cancel tasks")
  @RequestMapping(value = "/cancel", method = RequestMethod.PUT)
  Map cancelTasks(@RequestParam List<String> ids) {
    taskService.cancelTasks(ids)
  }

  @Operation(summary = "Get task details")
  @RequestMapping(value = "/{id}/details/{taskDetailsId}", method = RequestMethod.GET)
  Map getTaskDetails(@PathVariable("id") String id,
                     @PathVariable("taskDetailsId") String taskDetailsId,
                     @RequestHeader(value = "X-RateLimit-App", required = false) String sourceApp) {
    taskService.getTaskDetails(taskDetailsId, sourceApp)
  }
}
