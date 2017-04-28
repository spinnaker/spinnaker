package com.netflix.spinnaker.halyard.core.tasks.v1;

import com.netflix.spinnaker.halyard.core.DaemonResponse;
import com.netflix.spinnaker.halyard.core.error.v1.HalException;
import com.netflix.spinnaker.halyard.core.problem.v1.Problem;
import com.netflix.spinnaker.halyard.core.problem.v1.ProblemBuilder;
import com.netflix.spinnaker.halyard.core.problem.v1.ProblemSet;
import com.netflix.spinnaker.halyard.core.tasks.v1.DaemonTask.State;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import java.util.Date;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * All stored running/recently completed tasks.
 */
@Slf4j
public class TaskRepository {
  static final Map<String, DaemonTask> tasks = new ConcurrentHashMap<>();
  static final Map<String, ShallowTaskInfo> namedTasks = new ConcurrentHashMap<>();

  static public Map<String, ShallowTaskInfo> getTasks() {
    return namedTasks;
  }

  static private void deleteTaskInfo(String uuid) {
    tasks.remove(uuid);
    namedTasks.remove(uuid);
  }

  static public <C, T> DaemonTask<C, T> submitTask(Supplier<DaemonResponse<T>> runner, String name) {
    DaemonTask<C, T> task = new DaemonTask<>(name);
    String uuid = task.getUuid();
    log.info("Scheduling task " + task);
    Runnable r = () -> {
      Exception fatalError = null;
      State state = null;
      DaemonResponse<T> response = null;
      log.info("Starting task " + task);
      DaemonTaskHandler.setTask(task);
      task.setState(State.RUNNING);
      try {
        response = runner.get();
        state = State.SUCCESS;
      } catch (HalException e) {
        log.info("Task " + task + " failed for reason: ", e);
        response = new DaemonResponse<>(null, new ProblemSet(e.getProblems()));
        fatalError = e;
        state = State.FATAL;
      } catch (DaemonTaskInterrupted e) {
        log.info("Task " + task + " interrupted: ", e);
        response = new DaemonResponse<>(null, null);
        fatalError = e;
        state = State.INTERRUPTED;
        deleteTaskInfo(uuid);
      } catch (Exception e) {
        log.warn("Task " + task + " failed for unknown reason: ", e);
        Problem problem = new ProblemBuilder(Problem.Severity.FATAL, "Unknown exception: " + e).build();
        response = new DaemonResponse<>(null, new ProblemSet(problem));
        fatalError = e;
        state = State.FATAL;
      } finally {
        task.cleanupResources();
        task.setResponse(response);
        task.setState(state);
        task.setFatalError(fatalError);

        log.info("Task " + task + " completed");
        // Notify after changing state to avoid data-race where threads are notified before thread appears terminal
        synchronized (task) {
          task.notifyAll();
        }
      }
    };

    Thread t = new Thread(r);
    tasks.put(uuid, task.setRunner(t));
    namedTasks.put(uuid, new ShallowTaskInfo().setName(name).setStartDate(new Date().toString()));
    t.start();

    return task;
  }

  static public <C, T> DaemonTask<C, T> collectTask(String uuid) throws InterruptedException {
    DaemonTask task = tasks.get(uuid);
    if (task == null) {
      return null;
    }

    switch (task.getState()) {
      case NOT_STARTED:
      case RUNNING:
        break;
      case INTERRUPTED:
        log.warn("Task " + task + " interrupted.");
      case FATAL:
        log.warn("Task " + task + " encountered a fatal exception");
      case SUCCESS:
        log.info("Terminating task " + task);
        task.getRunner().join();

        deleteTaskInfo(uuid);
    }

    return task;
  }

  @Data
  static public class ShallowTaskInfo {
    String name;
    String startDate;
  }
}
