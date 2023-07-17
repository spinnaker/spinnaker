/*
 * Copyright 2022 Armory, Inc
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
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

package com.netflix.spinnaker.fiat.roles;

import java.util.concurrent.*;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Predicate;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class CallableCache<Key, Result> {

  private static final Predicate<Future> isExecutionInProgress =
      future -> !future.isDone() && !future.isCancelled();

  private final ExecutorService executor;
  private final ConcurrentHashMap<Key, Future<Result>> cache;
  private final Lock lock;

  public CallableCache() {
    this.executor = Executors.newCachedThreadPool();
    this.cache = new ConcurrentHashMap<>();
    this.lock = new ReentrantLock();
  }

  Future<Result> runAndGetResult(Key key, Callable<Result> callable) {
    try {
      lock.lock();
      if (isPresent(key)) {
        var futureResultFromCache = cache.get(key);
        if (isExecutionInProgress.test(futureResultFromCache)) {
          log.info(
              "There's running callable in cache associated with the key. Reusing that execution");
          return futureResultFromCache;
        }
      }
      log.debug("Starting new callable execution...");
      return cacheAndRun(key, callable);
    } finally {
      lock.unlock();
    }
  }

  void clear(Key key) {
    if (key == null) {
      return;
    }
    try {
      lock.lock();
      var future = cache.get(key);
      if (future != null && (future.isDone() || future.isCancelled())) {
        cache.remove(key);
        log.debug("Removing element from cache identified by key: " + key);
      }
    } finally {
      lock.unlock();
    }
  }

  private boolean isPresent(Key key) {
    return this.cache.containsKey(key);
  }

  private Future<Result> cacheAndRun(Key key, Callable<Result> callable) {
    var future = executor.submit(callable);
    this.cache.put(key, future);
    return future;
  }
}
