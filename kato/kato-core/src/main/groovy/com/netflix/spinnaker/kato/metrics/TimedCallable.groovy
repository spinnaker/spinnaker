/*
 * Copyright 2015 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.kato.metrics

import com.netflix.spectator.api.Id
import com.netflix.spectator.api.Registry
import groovy.transform.CompileStatic

import java.util.concurrent.Callable
import java.util.concurrent.TimeUnit

@CompileStatic
class TimedCallable<T> implements Callable<T> {
  private final Registry registry
  private final Id metricId
  private final Callable<T> callable

  private static class RunnableWrapper implements Callable<Void> {
    private final Runnable runnable

    RunnableWrapper(Runnable runnable) {
      this.runnable = runnable
    }

    @Override
    Void call() throws Exception {
      runnable.run()
      null
    }
  }

  private static class ClosureWrapper<T> implements Callable<T> {
    private final Closure<T> closure

    ClosureWrapper(Closure<T> closure) {
      this.closure = closure
    }

    @Override
    T call() throws Exception {
      closure.call()
    }
  }

  public static TimedCallable<Void> forRunnable(Registry registry, Id metricId, Runnable runnable) {
    new TimedCallable<Void>(registry, metricId, new RunnableWrapper(runnable))
  }

  public static <T> TimedCallable<T> forCallable(Registry registry, Id metricId, Callable<T> callable) {
    new TimedCallable<T>(registry, metricId, callable)
  }

  public static <T> TimedCallable<T> forClosure(Registry registry, Id metricId, Closure<T> closure) {
    new TimedCallable<T>(registry, metricId, new ClosureWrapper<T>(closure))
  }

  TimedCallable(Registry registry, Id metricId, Callable<T> callable) {
    this.registry = registry
    this.metricId = metricId
    this.callable = callable
  }

  @Override
  T call() throws Exception {
    long start = System.nanoTime()
    Id thisId = metricId
    try {
      T result = callable.call()
      thisId = thisId.withTag("success", "true")
      return result
    } catch (Exception ex) {
      thisId = thisId.withTag("success", "false").withTag("cause", ex.class.simpleName)
      throw ex
    } finally {
      registry.timer(thisId).record(System.nanoTime() - start, TimeUnit.NANOSECONDS)
    }
  }
}
