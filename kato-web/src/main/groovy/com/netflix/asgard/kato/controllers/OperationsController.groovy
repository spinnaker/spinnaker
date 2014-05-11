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

package com.netflix.asgard.kato.controllers

import com.netflix.asgard.kato.data.task.Task
import com.netflix.asgard.kato.data.task.TaskRepository
import com.netflix.asgard.kato.orchestration.AtomicOperation
import com.netflix.asgard.kato.orchestration.AtomicOperationConverter
import com.netflix.asgard.kato.orchestration.AtomicOperationNotFoundException
import com.netflix.asgard.kato.orchestration.DefaultOrchestrationProcessor
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.ApplicationContext
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestMethod
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/ops")
class OperationsController {
  @Autowired
  TaskRepository taskRepository

  @Autowired
  ApplicationContext applicationContext

  @RequestMapping(method = RequestMethod.POST)
  Map<String, String> deploy(@RequestBody List<Map<String, Map>> requestBody) {
    List<AtomicOperation> atomicOperations = requestBody.collect { Map<String, Map> input ->
      input.collect { k, v ->
        AtomicOperationConverter converter = null
        try {
          converter = (AtomicOperationConverter) applicationContext.getBean(k)
        } catch (IGNORE) {
        }
        if (!converter) {
          throw new AtomicOperationNotFoundException(k)
        }
        converter.convertOperation v
      }
    }?.flatten()

    Task task = DefaultOrchestrationProcessor.start(atomicOperations)
    [id: task.id, resourceUri: "/task/${task.id}".toString()]
  }

}
