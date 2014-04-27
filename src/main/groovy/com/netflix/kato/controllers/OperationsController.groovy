package com.netflix.kato.controllers

import com.netflix.kato.data.task.Task
import com.netflix.kato.data.task.TaskRepository
import com.netflix.kato.orchestration.*
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.ApplicationContext
import org.springframework.web.bind.annotation.*

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
        AtomicOperationConverter converter = (AtomicOperationConverter) applicationContext.getBean(k)
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
