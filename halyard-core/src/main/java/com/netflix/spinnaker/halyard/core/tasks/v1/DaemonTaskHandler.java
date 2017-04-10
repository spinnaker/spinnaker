package com.netflix.spinnaker.halyard.core.tasks.v1;

import com.netflix.spinnaker.halyard.core.DaemonResponse;
import com.netflix.spinnaker.halyard.core.problem.v1.ProblemSet;
import lombok.extern.slf4j.Slf4j;

import java.util.function.BiFunction;
import java.util.function.BinaryOperator;
import java.util.function.Supplier;

/**
 * Holds a thread-local task that can be logged to.
 */
@Slf4j
public class DaemonTaskHandler {
  private static ThreadLocal<DaemonTask> localTask = new ThreadLocal<>();

  static void setTask(DaemonTask task) {
    localTask.set(task);
  }

  public static DaemonTask getTask() {
    return localTask.get();
  }

  public static Object getContext() {
    return localTask.get().getContext();
  }

  public static <U, T> DaemonResponse<U> reduceChildren(U base, BiFunction<U, ? super T, U> accumulator, BinaryOperator<U> combiner) {
    DaemonTask task = getTask();
    if (task != null) {
      U responseBody = base;
      ProblemSet problemSet = new ProblemSet();
      DaemonResponse<U> response = new DaemonResponse<>(responseBody, problemSet);

      return (DaemonResponse) task.getChildren().stream().reduce(response,
          (o, t) -> {
            DaemonResponse<U> collector = (DaemonResponse<U>) o;
            DaemonTask child = (DaemonTask) t;
            DaemonResponse<T> childResponse = task.reapChild(child);
            collector.getProblemSet().addAll(childResponse.getProblemSet());
            collector.setResponseBody(accumulator.apply(collector.getResponseBody(), childResponse.getResponseBody()));
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

  public static <C, T> DaemonTask<C, T> submitTask(Supplier<DaemonResponse<T>> taskSupplier, String name) {
    DaemonTask task = getTask();
    DaemonTask<C, T> result;
    if (task != null) {
      result = task.spawnChild(taskSupplier, name);
      log.info(task + " spawned child " + result);
    } else {
      result = TaskRepository.submitTask(taskSupplier, name);
    }
    return result;
  }

  public static void setContext(Object context) {
    localTask.get().setContext(context);
  }

  public static void newStage(String name) {
    DaemonTask task = getTask();
    if (task != null) {
      log.info("Stage change by " + task + ": " + name);
      task.newStage(name);
    }
  }

  public static void message(String message) {
    DaemonTask task = getTask();
    if (task != null) {
      log.info("Message by " + task + ": " + message);
      task.writeMessage(message);
    }
  }
}
