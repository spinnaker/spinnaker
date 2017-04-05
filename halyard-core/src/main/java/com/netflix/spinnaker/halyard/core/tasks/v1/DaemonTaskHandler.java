package com.netflix.spinnaker.halyard.core.tasks.v1;

/**
 * Holds a thread-local task that can be logged to.
 */
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
    if (getTask() != null) {
      getTask().newStage(name);
    }
  }

  public static void message(String message) {
    if (getTask() != null) {
      getTask().writeMessage(message);
    }
  }

  public static void detail(String detail) {
    if (getTask() != null) {
      getTask().writeDetail(detail);
    }
  }
}
