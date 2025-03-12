package com.netflix.spinnaker.clouddriver.metrics;

import com.netflix.spectator.api.Id;
import com.netflix.spectator.api.Registry;
import com.netflix.spinnaker.kork.annotations.DeprecationInfo;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;

public class TimedCallable<T> implements Callable<T> {

  public static TimedCallable<Void> forRunnable(Registry registry, Id metricId, Runnable runnable) {
    return new TimedCallable<Void>(registry, metricId, new RunnableWrapper(runnable));
  }

  public static <T> TimedCallable<T> forCallable(
      Registry registry, Id metricId, Callable<T> callable) {
    return new TimedCallable<T>(registry, metricId, callable);
  }

  @Deprecated
  @DeprecationInfo(
      reason = "Groovy removal, no difference between this and forCallable",
      since = "1.22.0",
      eol = "1.23.0")
  public static <T> TimedCallable<T> forClosure(
      Registry registry, Id metricId, Callable<T> closure) {
    return new TimedCallable<T>(registry, metricId, new CallableWrapper<>(closure));
  }

  private final Registry registry;
  private final Id metricId;
  private final Callable<T> callable;

  public TimedCallable(Registry registry, Id metricId, Callable<T> callable) {
    this.registry = registry;
    this.metricId = metricId;
    this.callable = callable;
  }

  @Override
  public T call() throws Exception {
    long start = System.nanoTime();
    Id thisId = metricId;
    try {
      T result = callable.call();
      thisId = thisId.withTag("success", "true");
      return result;
    } catch (Exception ex) {
      thisId = thisId.withTag("success", "false").withTag("cause", ex.getClass().getSimpleName());
      throw ex;
    } finally {
      registry.timer(thisId).record(System.nanoTime() - start, TimeUnit.NANOSECONDS);
    }
  }

  private static class RunnableWrapper implements Callable<Void> {

    private final Runnable runnable;

    public RunnableWrapper(Runnable runnable) {
      this.runnable = runnable;
    }

    @Override
    public Void call() throws Exception {
      runnable.run();
      return null;
    }
  }

  private static class CallableWrapper<T> implements Callable<T> {

    private final Callable<T> closure;

    public CallableWrapper(Callable<T> closure) {
      this.closure = closure;
    }

    @Override
    public T call() throws Exception {
      return closure.call();
    }
  }
}
