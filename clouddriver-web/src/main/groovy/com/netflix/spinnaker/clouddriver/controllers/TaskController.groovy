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
import com.netflix.spinnaker.kork.web.exceptions.NotFoundException
import groovy.util.logging.Slf4j
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestMethod
import org.springframework.web.bind.annotation.RestController

import javax.annotation.PreDestroy
import java.util.concurrent.TimeUnit

@RequestMapping("/task")
@RestController
@Slf4j
class TaskController {
  @Autowired
  TaskRepository taskRepository

  @Value('${admin.tasks.shutdown-wait-seconds:-1}')
  Long shutdownWaitSeconds

  @RequestMapping(value = "/{id}", method = RequestMethod.GET)
  Task get(@PathVariable("id") String id) {
    Task t = taskRepository.get(id)
    if (!t) {
      throw new NotFoundException("Task not found (id: ${id})")
    }
    return t
  }

  @RequestMapping(method = RequestMethod.GET)
  List<Task> list() {
    taskRepository.list()
  }

  @PreDestroy
  public void destroy() {
    long start = System.currentTimeMillis()
    def tasks = taskRepository.listByThisInstance()
    while (tasks && !tasks.isEmpty() &&
        (System.currentTimeMillis() - start) / TimeUnit.SECONDS.toMillis(1) < shutdownWaitSeconds) {
      log.info("There are {} task(s) still running... sleeping before shutting down", tasks.size())
      sleep(1000)
      tasks = taskRepository.listByThisInstance()
    }

    if (tasks && !tasks.isEmpty()) {
      log.error("Shutting down while tasks '{}' are still in progress!", tasks)
    }
  }
}
