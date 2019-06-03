package com.netflix.spinnaker.halyard.core.tasks.v1;

import com.netflix.spinnaker.halyard.core.DaemonResponse;
import com.netflix.spinnaker.halyard.core.error.v1.HalException;
import com.netflix.spinnaker.halyard.core.tasks.v1.DaemonTask.State;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

/** All stored running/recently completed tasks. */
@Slf4j
public class TaskRepository {
  static final Map<String, DaemonTask> tasks = new ConcurrentHashMap<>();

  public static ShallowTaskList getTasks() {
    return new ShallowTaskList()
        .setTasks(
            tasks.keySet().stream()
                .map(t -> new ShallowTaskInfo(tasks.get(t)))
                .collect(Collectors.toList()));
  }

  // The amount of time before a task is collected after its timeout is invoked.
  private static long DELETE_TASK_INFO_WINDOW = TimeUnit.MINUTES.toMillis(2);
  public static long DEFAULT_TIMEOUT = TimeUnit.MINUTES.toMillis(1);

  private static void deleteTaskInfo(String uuid) {
    tasks.remove(uuid);
  }

  public static <C, T> DaemonTask<C, T> submitTask(
      Supplier<DaemonResponse<T>> runner, String name, long timeout) {
    DaemonTask<C, T> task = new DaemonTask<>(name, timeout);
    String uuid = task.getUuid();
    log.info("Scheduling task " + task);
    Runnable r =
        () -> {
          log.info("Starting task " + task);
          DaemonTaskHandler.setTask(task);
          task.setState(State.RUNNING);
          try {
            task.success(runner.get());
          } catch (HalException e) {
            log.info("Task " + task + " failed with HalException: ", e);
            task.failure(e);
          } catch (Exception e) {
            log.warn("Task " + task + " failed with unexpected reason: ", e);
            task.failure(e);
          } finally {
            task.cleanupResources();

            log.info("Task " + task + " completed");
            // Notify after changing state to avoid data-race where threads are notified before
            // thread appears terminal
            synchronized (task) {
              task.notifyAll();
            }
          }
        };

    Thread t = new Thread(r);
    tasks.put(uuid, task.setRunner(t));
    t.start();

    Thread interrupt = new Thread(new Interrupter(timeout, task));
    interrupt.start();

    return task;
  }

  private static class Interrupter implements Runnable {
    final long startTime;
    final long endTime;
    final DaemonTask target;

    Interrupter(long timeout, DaemonTask target) {
      this.startTime = System.currentTimeMillis();
      this.endTime = this.startTime + timeout;
      this.target = target;
    }

    @Override
    public void run() {
      long sleepTime = endTime - System.currentTimeMillis();
      while (sleepTime > 0) {
        try {
          Thread.sleep(sleepTime);
        } catch (InterruptedException ignored) {
        }

        sleepTime = endTime - System.currentTimeMillis();
      }

      switch (target.getState()) {
        case NOT_STARTED:
        case RUNNING:
          log.warn(
              "Interrupting task "
                  + target
                  + " that timed out after "
                  + (endTime - startTime)
                  + " millis.");
          target.timeout();
          break;
        case TIMED_OUT:
        case INTERRUPTED:
        case FAILED:
        case SUCCEEDED:
          log.info("Interrupter has no work to do, " + target + " already completed.");
          break;
      }

      try {
        Thread.sleep(DELETE_TASK_INFO_WINDOW);
      } catch (InterruptedException ignored) {
      }

      deleteTaskInfo(target.getUuid());
    }
  }

  @Data
  public static class ShallowTaskInfo {
    String uuid;
    String name;
    State state;
    String lastEvent;
    Exception fatalException;
    List<String> jobs = new ArrayList<>();
    List<String> children = new ArrayList<>();

    public ShallowTaskInfo() {}

    ShallowTaskInfo(DaemonTask task) {
      this.uuid = task.getUuid();
      this.name = task.getName();
      this.state = task.getState();
      this.fatalException = task.getFatalError();
      this.jobs = task.getRunningJobs();

      if (!task.getEvents().isEmpty()) {
        this.lastEvent = task.getEvents().get(task.getEvents().size() - 1).toString();
      }

      this.children =
          (List<String>)
              task.getChildren().stream().map(c -> c.toString()).collect(Collectors.toList());
    }
  }

  public static <C, T> DaemonTask<C, T> getTask(String uuid) {
    return tasks.get(uuid);
  }
}
