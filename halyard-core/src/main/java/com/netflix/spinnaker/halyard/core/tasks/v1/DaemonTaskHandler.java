package com.netflix.spinnaker.halyard.core.tasks.v1;

import lombok.extern.slf4j.Slf4j;

/**
 * Holds a thread-local task that can be logged to.
 */
@Slf4j
public class DaemonTaskHandler {
  private static ThreadLocal<DaemonTask> localTask = new ThreadLocal<>();

  static void setTask(DaemonTask task) {
    localTask.set(task);
  }

  private static DaemonTask getTask() {
    return localTask.get();
  }

  public static Object getContext() {
    return localTask.get().getContext();
  }

  public static void setContext(Object context) {
    localTask.get().setContext(context);
  }

  public static void newStage(String name) {
    DaemonTask task = getTask();
    if (task != null) {
      log.info("Stage change by " + task.getUuid() + ": " + name);
      task.newStage(name);
    }
  }

  public static void message(String message) {
    DaemonTask task = getTask();
    if (task != null) {
      log.info("Message by " + task.getUuid() + ": " + message);
      task.writeMessage(message);
    }
  }

  public static void detail(String detail) {
    DaemonTask task = getTask();
    if (task != null) {
      log.info("Detail update by " + task.getUuid() + ": " + detail);
      task.writeDetail(detail);
    }
  }
}
