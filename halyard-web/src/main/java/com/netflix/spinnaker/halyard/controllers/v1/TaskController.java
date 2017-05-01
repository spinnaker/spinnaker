package com.netflix.spinnaker.halyard.controllers.v1;

import com.netflix.spinnaker.halyard.config.model.v1.node.Halconfig;
import com.netflix.spinnaker.halyard.core.DaemonResponse;
import com.netflix.spinnaker.halyard.core.tasks.v1.DaemonTask;
import com.netflix.spinnaker.halyard.core.tasks.v1.DaemonTaskHandler;
import com.netflix.spinnaker.halyard.core.tasks.v1.ShallowTaskList;
import com.netflix.spinnaker.halyard.core.tasks.v1.TaskRepository;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import retrofit.http.Body;

@RestController
@RequestMapping("/v1/tasks")
public class TaskController {
  @RequestMapping(value = "/{uuid:.+}/", method = RequestMethod.GET)
  DaemonTask<Halconfig, Void> getTask(@PathVariable String uuid) {
    return TaskRepository.getTask(uuid);
  }

  @RequestMapping(value = "/{uuid:.+}/interrupt", method = RequestMethod.PUT)
  void interruptTask(@PathVariable String uuid, @Body String ignored) {
    DaemonTask task = TaskRepository.getTask(uuid);

    if (task == null) {
      throw new TaskNotFoundException("No such task with UUID " + uuid);
    }

    task.interrupt();
  }

  @RequestMapping(value = "/", method = RequestMethod.GET)
  ShallowTaskList getTasks() {
    return TaskRepository.getTasks();
  }

  @ResponseStatus(value = HttpStatus.NOT_FOUND)
  public final class TaskNotFoundException extends RuntimeException {
    public TaskNotFoundException(String msg) {
      super(msg);
    }
  }
}
