package com.netflix.spinnaker.clouddriver.metrics;

import com.netflix.spinnaker.kork.annotations.DeprecationInfo;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;

public class TimedCallable<T> implements Callable<T> {

  public static TimedCallable<Void> forRunnable(
      MeterRegistry registry, String metricName, Tags tags, Runnable runnable) {
    return new TimedCallable<Void>(registry, metricName, tags, new RunnableWrapper(runnable));
  }

  public static <T> TimedCallable<T> forCallable(
      MeterRegistry registry, String metricName, Tags tags, Callable<T> callable) {
    return new TimedCallable<T>(registry, metricName, tags, callable);
  }

  @Deprecated
  @DeprecationInfo(
      reason = "Groovy removal, no difference between this and forCallable",
      since = "1.22.0",
      eol = "1.23.0")
  public static <T> TimedCallable<T> forClosure(
      MeterRegistry registry, String metricName, Tags tags, Callable<T> closure) {
    return new TimedCallable<T>(registry, metricName, tags, new CallableWrapper<>(closure));
  }

  private final MeterRegistry registry;
  private final String metricName;
  private final Tags tags;
  private final Callable<T> callable;

  public TimedCallable(MeterRegistry registry, String metricName, Tags tags, Callable<T> callable) {
    this.registry = registry;
    this.metricName = metricName;
    this.tags = tags;
    this.callable = callable;
  }

  @Override
  public T call() throws Exception {
    long start = System.nanoTime();
    Tags thisTags = tags;
    try {
      T result = callable.call();
      thisTags = thisTags.and("success", "true");
      return result;
    } catch (Exception ex) {
      thisTags = thisTags.and("success", "false", "cause", ex.getClass().getSimpleName());
      throw ex;
    } finally {
      registry.timer(metricName, thisTags).record(System.nanoTime() - start, TimeUnit.NANOSECONDS);
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
