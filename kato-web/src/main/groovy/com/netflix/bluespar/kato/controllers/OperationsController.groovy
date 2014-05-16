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

package com.netflix.bluespar.kato.controllers

import com.netflix.bluespar.kato.data.task.Task
import com.netflix.bluespar.kato.orchestration.AtomicOperation
import com.netflix.bluespar.kato.orchestration.AtomicOperationConverter
import com.netflix.bluespar.kato.orchestration.AtomicOperationNotFoundException
import com.netflix.bluespar.kato.orchestration.OrchestrationProcessor
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.ApplicationContext
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/ops")
class OperationsController {
  @Autowired
  ApplicationContext applicationContext

  @Autowired
  OrchestrationProcessor orchestrationProcessor

  @RequestMapping(method = RequestMethod.POST)
  Map<String, String> deploy(@RequestBody List<Map<String, Map>> requestBody) {
    List<AtomicOperation> atomicOperations = requestBody.collect { Map<String, Map> input ->
      convert(input)
    }?.flatten()
    start atomicOperations
  }

  @RequestMapping(value = "/{name}", method = RequestMethod.POST)
  Map<String, String> atomic(@PathVariable("name") String name, @RequestBody Map requestBody) {
    List<AtomicOperation> atomicOperations = convert([(name): requestBody])
    start atomicOperations
  }

  private Map<String, String> start(List<AtomicOperation> atomicOperations) {
    Task task = orchestrationProcessor.process(atomicOperations)
    [id: task.id, resourceUri: "/task/${task.id}".toString()]
  }

  private List<AtomicOperation> convert(Map<String, Map> input) {
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
  }

}
