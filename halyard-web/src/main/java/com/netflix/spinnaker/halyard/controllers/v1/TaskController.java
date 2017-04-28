package com.netflix.spinnaker.halyard.controllers.v1;

import com.netflix.spinnaker.halyard.config.model.v1.node.Halconfig;
import com.netflix.spinnaker.halyard.core.DaemonResponse;
import com.netflix.spinnaker.halyard.core.tasks.v1.DaemonTask;
import com.netflix.spinnaker.halyard.core.tasks.v1.DaemonTaskHandler;
import com.netflix.spinnaker.halyard.core.tasks.v1.DaemonTaskInterrupted;
import com.netflix.spinnaker.halyard.core.tasks.v1.TaskRepository;
import com.netflix.spinnaker.halyard.core.tasks.v1.TaskRepository.ShallowTaskInfo;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import retrofit.http.Body;

import java.util.Map;

@RestController
@RequestMapping("/v1/tasks/")
public class TaskController {
  @RequestMapping(value = "/{uuid:.+}/", method = RequestMethod.GET)
  DaemonTask<Halconfig, Void> getTask(@PathVariable String uuid) {
    try {
      return TaskRepository.collectTask(uuid);
    } catch (InterruptedException e) {
      throw new DaemonTaskInterrupted(e);
    }
  }

  @RequestMapping(value = "/{uuid:.+}/interrupt", method = RequestMethod.PUT)
  void interruptTask(@PathVariable String uuid, @Body String ignored) {
    DaemonTask task = null;
    try {
      task = TaskRepository.collectTask(uuid);
    } catch (InterruptedException e) {
      throw new DaemonTaskInterrupted(e);
    }

    if (task == null) {
      throw new TaskNotFoundException("No such task with UUID " + uuid);
    }

    task.interrupt();
  }

  @RequestMapping(value = "/", method = RequestMethod.GET)
  DaemonTask<Halconfig, Map<String, ShallowTaskInfo>> getTasks() {
    DaemonResponse.StaticRequestBuilder<Map<String, ShallowTaskInfo>> builder = new DaemonResponse.StaticRequestBuilder<>();
    builder.setBuildResponse(TaskRepository::getTasks);
    return DaemonTaskHandler.submitTask(builder::build, "List currently running tasks");
  }

  @ResponseStatus(value = HttpStatus.NOT_FOUND)
  public final class TaskNotFoundException extends RuntimeException {
    public TaskNotFoundException(String msg) {
      super(msg);
    }
  }
}
