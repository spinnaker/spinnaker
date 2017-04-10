package com.netflix.spinnaker.halyard.core.tasks.v1;

import com.netflix.spinnaker.halyard.core.DaemonResponse;
import com.netflix.spinnaker.halyard.core.error.v1.HalException;
import com.netflix.spinnaker.halyard.core.problem.v1.ProblemSet;
import com.netflix.spinnaker.halyard.core.tasks.v1.DaemonTask.State;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
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

  static public List<String> getTasks() {
    return new ArrayList<>(tasks.keySet());
  }

  static public <C, T> DaemonTask<C, T> submitTask(Supplier<DaemonResponse<T>> runner, String name) {
    DaemonTask<C, T> task = new DaemonTask<C, T>(name);
    String uuid = task.getUuid();
    log.info("Scheduling task " + task);
    Runnable r = () -> {
      log.info("Starting task " + task);
      DaemonTaskHandler.setTask(task);
      task.setState(State.RUNNING);
      try {
        task.setResponse(runner.get());
        task.setState(State.SUCCESS);
      } catch (Exception e) {
        log.info("Task " + task + " failed");
        task.setFatalError(e);
        task.setState(State.FATAL);
      } finally {
        // Notify after changing state to avoid data-race where threads are notified before thread appears terminal
        synchronized (task) {
          task.notifyAll();
        }
      }
      log.info("Task " + task + " completed");
    };

    Thread t = new Thread(r);
    tasks.put(uuid, new DaemonTaskStatus()
        .setRunner(t)
        .setTask(task));
    t.start();

    return task;
  }

  static public <C, T> DaemonTask<C, T> getTask(String uuid) {
    DaemonTaskStatus status = tasks.get(uuid);
    if (status == null) {
      return null;
    }

    DaemonTask<C, T> task = status.getTask();
    Exception fatalError = null;
    switch (task.getState()) {
      case NOT_STARTED:
      case RUNNING:
        break;
      case FATAL:
        log.error("Task " + task + " encountered a fatal exception");
        fatalError = task.getFatalError();
      case SUCCESS:
        log.info("Terminating task " + task);
        try {
          status.getRunner().join();
        } catch (InterruptedException ignored) {
        }

        tasks.remove(uuid);
    }

    if (fatalError != null) {
      if (fatalError instanceof HalException) {
        HalException halException = (HalException) fatalError;
        ProblemSet problemSet = halException.getProblems();
        if (task.getResponse() != null) {
          task.getResponse().getProblemSet().addAll(problemSet);
        } else {
          task.setResponse(new DaemonResponse<>(null, problemSet));
        }
      } else {
        throw new RuntimeException("Unknown error encountered while running task: " + fatalError.getMessage(), fatalError);
      }
    }

    return task;
  }

  @Data
  static private class DaemonTaskStatus {
    DaemonTask task;
    Thread runner;
  }
}
