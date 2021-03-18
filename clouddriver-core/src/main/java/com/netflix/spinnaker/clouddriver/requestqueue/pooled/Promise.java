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
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

class Promise<T> {
  private static class Either<T> {
    private final T result;
    private final Throwable exception;

    static <T> Either<T> forResult(T result) {
      return new Either<>(result, null);
    }

    static <T> Either<T> forException(Throwable exception) {
      return new Either<>(null, exception);
    }

    Either(T result, Throwable exception) {
      this.result = result;
      this.exception = exception;
    }

    T getOrThrow() throws Throwable {
      if (exception != null) {
        throw exception;
      }

      return result;
    }
  }

  private final CountDownLatch startingLatch = new CountDownLatch(1);
  private final CountDownLatch latch = new CountDownLatch(1);
  private final AtomicReference<Either<T>> result = new AtomicReference<>();
  private final Registry registry;
  private final String partition;

  Promise(Registry registry, String partition) {
    this.registry = registry;
    this.partition = partition;
  }

  boolean shouldStart() {
    try {
      return result.get() == null;
    } finally {
      startingLatch.countDown();
    }
  }

  void complete(T result) {
    registry
        .counter(registry.createId("pooledRequestQueue.promise.complete", "partition", partition))
        .increment();
    this.result.compareAndSet(null, Either.forResult(result));
    startingLatch.countDown();
    latch.countDown();
  }

  void completeWithException(Throwable exception) {
    final String cause =
        Optional.ofNullable(exception)
            .map(Throwable::getClass)
            .map(Class::getSimpleName)
            .orElse("unknown");
    registry
        .counter(
            registry.createId(
                "pooledRequestQueue.promise.exception", "partition", partition, "cause", cause))
        .increment();
    this.result.compareAndSet(null, Either.forException(exception));
    startingLatch.countDown();
    latch.countDown();
  }

  T blockingGetOrThrow(long startWorkTimeout, long timeout, TimeUnit unit) throws Throwable {
    try {
      if (startingLatch.await(startWorkTimeout, unit)) {
        if (!latch.await(timeout, unit)) {
          registry
              .counter(
                  registry.createId("pooledRequestQueue.promise.timeout", "partition", partition))
              .increment();
          completeWithException(new PromiseTimeoutException());
        }
      } else {
        registry
            .counter(registry.createId("pooledRequest.promise.notStarted", "partition", partition))
            .increment();
        completeWithException(new PromiseNotStartedException());
      }
    } catch (Throwable t) {
      completeWithException(t);
    }
    return this.result.get().getOrThrow();
  }
}
