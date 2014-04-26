package com.netflix.kato.controllers

import com.netflix.kato.data.task.Task
import com.netflix.kato.data.task.TaskRepository
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.web.bind.annotation.*

@RequestMapping("/task")
@RestController
class TaskController {
  @Autowired
  TaskRepository taskRepository

  @RequestMapping(value = "/{id}", method = RequestMethod.GET)
  Task get(@PathVariable("id") String id) {
    taskRepository.get id
  }

  @RequestMapping(method = RequestMethod.GET)
  List<Task> list() {
    taskRepository.list()
  }
}
