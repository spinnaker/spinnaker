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

  public static void log(String message) {
    if (getTask() != null) {
      getTask().writeEvent(message);
    }
  }
}
