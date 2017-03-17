package com.netflix.spinnaker.halyard.controllers.v1;

import com.netflix.spinnaker.halyard.config.model.v1.node.Halconfig;
import com.netflix.spinnaker.halyard.core.tasks.v1.DaemonTask;
import com.netflix.spinnaker.halyard.core.tasks.v1.TaskRepository;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/v1/tasks/")
public class TaskController {
  @RequestMapping(value = "/{uuid:.+}/", method = RequestMethod.GET)
  DaemonTask<Halconfig, Void> getTask(@PathVariable String uuid) {
    return TaskRepository.getTask(uuid);
  }

  @RequestMapping(value = "/", method = RequestMethod.GET)
  List<String> getTasks() {
    return TaskRepository.getTasks();
  }
}
