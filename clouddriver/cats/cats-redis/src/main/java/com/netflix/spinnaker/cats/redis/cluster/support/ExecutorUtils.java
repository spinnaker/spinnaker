/*
 * Copyright 2025 Harness, Inc.
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

package com.netflix.spinnaker.cats.redis.cluster.support;

import lombok.extern.slf4j.Slf4j;

/**
 * Executor helpers for the Priority Redis Scheduler.
 *
 * <p>Responsibilities: - Provide named executors with daemon threads and consistent error handling
 * - Avoid idle thread bloat via on-demand single-thread executors
 *
 * <p>Non-responsibilities: - Cadence or time logic (use CadenceGuard/RedisTimeUtils) - Redis
 * script/result handling (use RedisScriptManager/ScriptResults)
 */
@Slf4j
public final class ExecutorUtils {
  private ExecutorUtils() {}

  /**
   * Creates an on-demand single-thread executor: no core threads, one max thread, configurable
   * keep-alive, and daemon threads with a friendly name. The thread is created only when a task is
   * submitted and will be terminated after the idle period, keeping thread metrics clean during
   * idle windows.
   *
   * @param threadNamePattern thread name pattern with '#' as placeholder for thread number
   * @param keepAliveMs idle time before thread termination (clamped to at least 1ms)
   * @return configured executor service
   */
  public static java.util.concurrent.ExecutorService newOnDemandSingleThreadExecutor(
      String threadNamePattern, long keepAliveMs) {
    java.util.concurrent.ThreadPoolExecutor exec =
        new java.util.concurrent.ThreadPoolExecutor(
            0,
            1,
            Math.max(1L, keepAliveMs),
            java.util.concurrent.TimeUnit.MILLISECONDS,
            new java.util.concurrent.SynchronousQueue<>(),
            r -> {
              Thread workerThread = new Thread(r, threadNamePattern.replace("#", "0"));
              workerThread.setDaemon(true);
              workerThread.setUncaughtExceptionHandler(
                  (thread, throwable) ->
                      log.error(
                          "Uncaught exception in {}: {}",
                          thread.getName(),
                          String.valueOf(throwable.getMessage()),
                          throwable));
              return workerThread;
            });
    exec.allowCoreThreadTimeOut(true);
    return exec;
  }

  /**
   * Creates a cached thread pool with a named thread factory. Threads are daemon and use the
   * provided name format.
   *
   * @param nameFormat thread name format with '%d' as placeholder (e.g., "PriorityAgentWorker-%d")
   * @return configured cached thread pool
   */
  public static java.util.concurrent.ExecutorService newNamedCachedThreadPool(String nameFormat) {
    com.google.common.util.concurrent.ThreadFactoryBuilder builder =
        new com.google.common.util.concurrent.ThreadFactoryBuilder()
            .setNameFormat(nameFormat)
            .setDaemon(true)
            .setUncaughtExceptionHandler(
                (thread, throwable) ->
                    log.error(
                        "Uncaught exception in {}: {}",
                        thread.getName(),
                        String.valueOf(throwable.getMessage()),
                        throwable));
    return java.util.concurrent.Executors.newCachedThreadPool(builder.build());
  }

  /**
   * Creates a single-thread scheduled executor with a named thread factory. Thread is daemon and
   * uses the provided name format.
   *
   * @param nameFormat thread name format with '%d' as placeholder (e.g.,
   *     "PriorityAgentScheduler-%d")
   * @return configured scheduled executor service
   */
  public static java.util.concurrent.ScheduledExecutorService newNamedSingleThreadScheduledExecutor(
      String nameFormat) {
    com.google.common.util.concurrent.ThreadFactoryBuilder builder =
        new com.google.common.util.concurrent.ThreadFactoryBuilder()
            .setNameFormat(nameFormat)
            .setDaemon(true)
            .setUncaughtExceptionHandler(
                (thread, throwable) ->
                    log.error(
                        "Uncaught exception in {}: {}",
                        thread.getName(),
                        String.valueOf(throwable.getMessage()),
                        throwable));
    return java.util.concurrent.Executors.newSingleThreadScheduledExecutor(builder.build());
  }
}
