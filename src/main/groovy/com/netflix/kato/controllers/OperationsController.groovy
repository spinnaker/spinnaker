package com.netflix.kato.controllers

import com.netflix.kato.data.task.Task
import com.netflix.kato.data.task.TaskRepository
import com.netflix.kato.orchestration.*
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/ops")
class OperationsController {
  @Autowired
  TaskRepository taskRepository

  @RequestMapping(method = RequestMethod.POST)
  Map<String, String> deploy(@RequestBody Map<String, Map> requestBody) {
    List<AtomicOperation> atomicOperations = requestBody.collect { k, v ->
      AtomicOperationConverter converter = AtomicOperationFactory.valueOf(k)
      if (!converter) {
        throw new AtomicOperationNotFoundException(k)
      }
      converter.convertOperation v
    }

    Task task = DefaultOrchestrationProcessor.start(atomicOperations)
    [id: task.id, resourceUri: "/task/${task.id}".toString()]
  }

}
