/*
 * Copyright 2017 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.clouddriver.requestqueue.pooled;

import com.netflix.spectator.api.Registry;
import com.netflix.spectator.api.Timer;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CancellationException;
import java.util.concurrent.TimeUnit;
import org.slf4j.MDC;

class PooledRequest<T> implements Runnable {
  private final Timer timer;
  private final Promise<T> result;
  private final Callable<T> work;
  private final long startTime = System.nanoTime();

  PooledRequest(Registry registry, String partition, Callable<T> work) {
    this.timer =
        registry.timer(registry.createId("pooledRequestQueue.enqueueTime", "partition", partition));
    this.result = new Promise<>(registry, partition);

    // Copy the MDC before doing the work.  That way information from the MDC
    // (e.g. from X-SPINNAKER-* incoming http request headers) of the calling
    // thread makes it into log messages.
    this.work = wrapWithContext(work);
  }

  Promise<T> getPromise() {
    return result;
  }

  void cancel() {
    result.completeWithException(new CancellationException());
  }

  private <T> Callable<T> wrapWithContext(final Callable<T> callable) {
    Map<String, String> contextMap = MDC.getCopyOfContextMap();
    return () -> {
      if (contextMap == null) {
        MDC.clear();
      } else {
        MDC.setContextMap(contextMap);
      }
      return callable.call();
    };
  }

  @Override
  public void run() {
    timer.record(System.nanoTime() - startTime, TimeUnit.NANOSECONDS);
    // request may have expired with a timeout prior to this point, lets not
    // issue the work if that is the case as the caller has already moved on
    if (result.shouldStart()) {
      try {
        result.complete(work.call());
      } catch (Throwable t) {
        result.completeWithException(t);
      }
    }
  }
}
