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

package com.netflix.spinnaker.clouddriver.controllers

import com.netflix.spinnaker.clouddriver.data.task.Task
import com.netflix.spinnaker.clouddriver.data.task.TaskRepository
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestMethod
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

import javax.servlet.http.HttpServletRequest

@RequestMapping("/task")
@RestController
class TaskController {
  @Autowired
  TaskRepository taskRepository

  @RequestMapping(value = "/{id}", method = RequestMethod.GET)
  Task get(@PathVariable("id") String id) {
    Task t = taskRepository.get(id)
    if (!t) {
      throw new TaskNotFoundException(id)
    }
    return t
  }

  @RequestMapping(method = RequestMethod.GET)
  List<Task> list() {
    taskRepository.list()
  }

  @ExceptionHandler(TaskNotFoundException)
  @ResponseStatus(HttpStatus.NOT_FOUND)
  Map handleTaskNotFoundException(HttpServletRequest req, TaskNotFoundException e) {
    [id: e.id, resourceUri: "/task/$e.id".toString(), status: HttpStatus.NOT_FOUND.value(), reason: HttpStatus.NOT_FOUND.reasonPhrase]
  }


  static class TaskNotFoundException extends Exception {
    final String id

    public TaskNotFoundException(String id) {
      super("Task $id not found")
      this.id = id
    }
  }
}
