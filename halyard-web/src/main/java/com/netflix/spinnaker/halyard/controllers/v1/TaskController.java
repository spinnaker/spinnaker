package com.netflix.spinnaker.halyard.controllers.v1;

import com.netflix.spinnaker.halyard.core.tasks.v1.DaemonTask;
import com.netflix.spinnaker.halyard.core.tasks.v1.TaskRepository;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/tasks/")
public class TaskController {
  @RequestMapping(value = "/{uuid:.+}/", method = RequestMethod.GET)
  DaemonTask<Void> generateConfig(@PathVariable String uuid) {
    return TaskRepository.getTask(uuid);
  }
}
