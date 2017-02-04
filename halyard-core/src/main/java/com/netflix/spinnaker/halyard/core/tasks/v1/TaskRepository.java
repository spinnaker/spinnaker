package com.netflix.spinnaker.halyard.core.tasks.v1;

import com.netflix.spinnaker.halyard.core.DaemonResponse;
import com.netflix.spinnaker.halyard.core.tasks.v1.DaemonTask.State;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * All stored running/recently completed tasks.
 */
@Slf4j
public class TaskRepository {
  static final Map<String, DaemonTaskStatus> tasks = new ConcurrentHashMap<>();

  static public <T> DaemonTask<T> submitTask(Supplier<DaemonResponse<T>> runner) {
    String uuid = UUID.randomUUID().toString();
    log.info("Scheduling task " + uuid);
    DaemonTask<T> task = new DaemonTask<T>().setUuid(uuid);
    Runnable r = () -> {
      log.info("Starting task " + uuid);
      DaemonTaskHandler.setTask(task);
      task.setState(State.RUNNING);
      try {
        task.setResponse(runner.get());
      } catch (Exception e) {
        log.info("Task " + uuid + " failed");
        task.setState(State.FATAL);
        task.setFatalError(e);
      }
      log.info("Task " + uuid + " completed");
      task.setState(State.COMPLETED);
    };

    Thread t = new Thread(r);
    tasks.put(uuid, new DaemonTaskStatus().setRunner(t).setTask(task));
    t.start();

    return task;
  }

  static public <T> DaemonTask<T> getTask(String uuid) {
    DaemonTaskStatus status = tasks.get(uuid);

    if (status == null) {
      return null;
    }
    DaemonTask<T> task = status.getTask();
    Exception fatalError = null;
    switch (task.getState()) {
      case NOT_STARTED:
      case RUNNING:
        break;
      case FATAL:
        log.error("Task " + uuid + " encountered a fatal exception");
        fatalError = task.getFatalError();
      case COMPLETED:
        log.info("Terminating task " + uuid);
        try {
          status.getRunner().join();
        } catch (InterruptedException ignored) {
        }

        tasks.remove(uuid);
    }

    if (fatalError != null) {
      throw new RuntimeException("Failed running task: " + fatalError.getMessage(), fatalError);
    }
    return task;
  }

  @Data
  static private class DaemonTaskStatus {
    DaemonTask task;
    Thread runner;
  }
}
