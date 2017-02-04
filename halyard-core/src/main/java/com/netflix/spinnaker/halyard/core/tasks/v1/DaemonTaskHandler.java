package com.netflix.spinnaker.halyard.core.tasks.v1;

/**
 * Holds a thread-local task that can be logged to.
 */
public class DaemonTaskHandler {
  private static ThreadLocal<DaemonTask> localTask = new ThreadLocal<>();

  public static void setTask(DaemonTask task) {
    localTask.set(task);
  }

  public static DaemonTask getTask() {
    return localTask.get();
  }

  public static void log(String message) {
    localTask.get().writeEvent(message);
  }
}
