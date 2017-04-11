package com.netflix.spinnaker.halyard.core.tasks.v1;

import com.netflix.spinnaker.halyard.core.DaemonResponse;
import com.netflix.spinnaker.halyard.core.error.v1.HalException;
import com.netflix.spinnaker.halyard.core.problem.v1.Problem;
import com.netflix.spinnaker.halyard.core.problem.v1.ProblemBuilder;
import com.netflix.spinnaker.halyard.core.problem.v1.ProblemSet;
import com.netflix.spinnaker.halyard.core.tasks.v1.DaemonTask.State;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
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
    DaemonTask<C, T> task = new DaemonTask<>(name);
    String uuid = task.getUuid();
    log.info("Scheduling task " + task);
    Runnable r = () -> {
      log.info("Starting task " + task);
      DaemonTaskHandler.setTask(task);
      task.setState(State.RUNNING);
      try {
        DaemonResponse<T> response = runner.get();
        task.setResponse(response);
        task.setState(State.SUCCESS);
      } catch (HalException e) {
        log.info("Task " + task + " failed for reason: ", e);
        task.setResponse(new DaemonResponse<>(null, new ProblemSet(e.getProblems())));
        task.setFatalError(e);
        task.setState(State.FATAL);
      } catch (Exception e) {
        log.warn("Task " + task + " failed for unknown reason: ", e);
        Problem problem = new ProblemBuilder(Problem.Severity.FATAL, "Unknown exception: " + e).build();
        task.setResponse(new DaemonResponse<>(null, new ProblemSet(problem)));
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
    switch (task.getState()) {
      case NOT_STARTED:
      case RUNNING:
        break;
      case FATAL:
        log.warn("Task " + task + " encountered a fatal exception");
      case SUCCESS:
        log.info("Terminating task " + task);
        try {
          status.getRunner().join();
        } catch (InterruptedException ignored) {
        }

        tasks.remove(uuid);
    }

    return task;
  }

  @Data
  static private class DaemonTaskStatus {
    DaemonTask task;
    Thread runner;
  }
}
