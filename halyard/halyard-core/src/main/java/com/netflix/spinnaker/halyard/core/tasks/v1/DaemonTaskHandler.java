package com.netflix.spinnaker.halyard.core.tasks.v1;

import com.netflix.spinnaker.halyard.core.DaemonResponse;
import com.netflix.spinnaker.halyard.core.error.v1.HalException;
import com.netflix.spinnaker.halyard.core.job.v1.DaemonLocalJobExecutor;
import com.netflix.spinnaker.halyard.core.job.v1.JobExecutor;
import com.netflix.spinnaker.halyard.core.problem.v1.ProblemSet;
import java.util.function.BiFunction;
import java.util.function.BinaryOperator;
import java.util.function.Supplier;
import lombok.extern.slf4j.Slf4j;

/** Holds a thread-local task that can be logged to. */
@Slf4j
public class DaemonTaskHandler {
  private static ThreadLocal<DaemonTask> localTask = new ThreadLocal<>();
  private static JobExecutor jobExecutor;

  static void setTask(DaemonTask task) {
    localTask.set(task);
  }

  public static DaemonTask getTask() {
    return localTask.get();
  }

  public static Object getContext() {
    return localTask.get().getContext();
  }

  public static <U, T> DaemonResponse<U> reduceChildren(
      U base, BiFunction<U, ? super T, U> accumulator, BinaryOperator<U> combiner) {
    DaemonTask task = getTask();
    if (task != null) {
      U responseBody = base;
      ProblemSet problemSet = new ProblemSet();
      DaemonResponse<U> response = new DaemonResponse<>(responseBody, problemSet);

      return (DaemonResponse)
          task.getChildren().stream()
              .reduce(
                  response,
                  (o, t) -> {
                    DaemonResponse<U> collector = (DaemonResponse<U>) o;
                    DaemonTask child = (DaemonTask) t;
                    DaemonResponse<T> childResponse;
                    try {
                      childResponse = task.reapChild(child);
                    } catch (InterruptedException e) {
                      throw new DaemonTaskInterrupted("Interrupted during reap", e);
                    }

                    DaemonTask.State state = child.getState();
                    if (!state.isTerminal()) {
                      throw new IllegalStateException(
                          "Child task " + child + " reaped but non-terminal.");
                    }

                    switch (state) {
                      case FAILED:
                        throw new HalException(childResponse.getProblemSet().getProblems());
                      case INTERRUPTED:
                        task.interrupt();
                        throw new DaemonTaskInterrupted(child.getFatalError());
                      case TIMED_OUT:
                        task.timeout();
                        throw new DaemonTaskInterrupted("Child task timed out");
                      case SUCCEEDED:
                        break;
                      default:
                        throw new IllegalStateException("Unknown terminal state " + state);
                    }

                    collector.getProblemSet().addAll(childResponse.getProblemSet());
                    collector.setResponseBody(
                        accumulator.apply(
                            collector.getResponseBody(), childResponse.getResponseBody()));
                    return collector;
                  },
                  (Object o1, Object o2) -> {
                    DaemonResponse<U> r1 = (DaemonResponse<U>) o1;
                    DaemonResponse<U> r2 = (DaemonResponse<U>) o2;
                    r1.setResponseBody(combiner.apply(r1.getResponseBody(), r2.getResponseBody()));
                    r1.getProblemSet().addAll(r2.getProblemSet());
                    return r1;
                  });
    } else {
      throw new IllegalStateException("You must be running a DaemonTask to reduce child tasks");
    }
  }

  public static <C, T> DaemonTask<C, T> submitTask(
      Supplier<DaemonResponse<T>> taskSupplier, String name, long timeout) {
    DaemonTask task = getTask();
    DaemonTask<C, T> result;
    if (task != null) {
      result = task.spawnChild(taskSupplier, name, timeout);
      log.info(task + " spawned child " + result);
    } else {
      result = TaskRepository.submitTask(taskSupplier, name, timeout);
    }

    return result;
  }

  public static <C, T> DaemonTask<C, T> submitTask(
      Supplier<DaemonResponse<T>> taskSupplier, String name) {
    DaemonTask task = getTask();
    long timeout = task != null ? task.getTimeout() : TaskRepository.DEFAULT_TIMEOUT;
    return submitTask(taskSupplier, name, timeout);
  }

  public static JobExecutor getJobExecutor() {
    if (getTask() == null) {
      throw new IllegalStateException("Cannot request a job executor from outside a daemon task");
    }

    if (jobExecutor == null) {
      jobExecutor = new DaemonLocalJobExecutor();
    }

    return jobExecutor;
  }

  public static void setContext(Object context) {
    localTask.get().setContext(context);
  }

  public static void newStage(String name) {
    if (Thread.interrupted()) {
      throw new DaemonTaskInterrupted();
    }

    DaemonTask task = getTask();
    if (task != null) {
      log.info("Stage change by " + task + ": " + name);
      task.newStage(name);
    }
  }

  public static void message(String message) {
    if (Thread.interrupted()) {
      throw new DaemonTaskInterrupted();
    }

    DaemonTask task = getTask();
    if (task != null) {
      log.info("Message by " + task + ": " + message);
      task.writeMessage(message);
    }
  }

  public static void safeSleep(Long millis) {
    if (Thread.interrupted()) {
      throw new DaemonTaskInterrupted();
    }

    try {
      Thread.sleep(millis);
    } catch (InterruptedException e) {
      throw new DaemonTaskInterrupted(e);
    }
  }
}
