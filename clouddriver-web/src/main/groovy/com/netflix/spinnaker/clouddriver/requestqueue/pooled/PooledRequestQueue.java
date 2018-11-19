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

import com.netflix.spectator.api.Id;
import com.netflix.spectator.api.Registry;
import com.netflix.spinnaker.clouddriver.requestqueue.RequestQueue;
import com.netflix.spinnaker.kork.dynamicconfig.DynamicConfigService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;

import javax.annotation.PreDestroy;
import java.util.Collection;
import java.util.Queue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public class PooledRequestQueue implements RequestQueue {
  private final Logger log = LoggerFactory.getLogger(getClass());
  private final ConcurrentMap<String, Queue<PooledRequest<?>>> partitionedRequests = new ConcurrentHashMap<>();
  private final PollCoordinator pollCoordinator = new PollCoordinator();

  private final long defaultStartWorkTimeout;
  private final long defaultTimeout;
  private final int defaultCorePoolSize;
  private final ThreadPoolExecutor executorService;
  private final BlockingQueue<Runnable> submittedRequests;
  private final Collection<Queue<PooledRequest<?>>> requestQueues;
  private final RequestDistributor requestDistributor;

  private final DynamicConfigService dynamicConfigService;
  private final Registry registry;

  private final AtomicBoolean isEnabled = new AtomicBoolean(true);

  public PooledRequestQueue(DynamicConfigService dynamicConfigService,
                            Registry registry,
                            long defaultStartWorkTimeout,
                            long defaultTimeout,
                            int requestPoolSize) {

    if (defaultStartWorkTimeout <= 0) {
      throw new IllegalArgumentException("defaultStartWorkTimeout");
    }

    if (defaultTimeout <= 0) {
      throw new IllegalArgumentException("defaultTimeout");
    }

    if (requestPoolSize < 1) {
      throw new IllegalArgumentException("requestPoolSize");
    }

    this.dynamicConfigService = dynamicConfigService;
    this.registry = registry;

    this.defaultStartWorkTimeout = defaultStartWorkTimeout;
    this.defaultTimeout = defaultTimeout;
    this.defaultCorePoolSize = requestPoolSize;

    this.submittedRequests = new LinkedBlockingQueue<>();
    registry.gauge("pooledRequestQueue.executorQueue.size", submittedRequests, Queue::size);

    final int actualThreads = requestPoolSize + 1;
    this.executorService = new ThreadPoolExecutor(actualThreads, actualThreads, 0, TimeUnit.MILLISECONDS, submittedRequests);
    registry.gauge("pooledRequestQueue.corePoolSize", executorService, ThreadPoolExecutor::getCorePoolSize);

    this.requestQueues = new CopyOnWriteArrayList<>();
    this.requestDistributor = new RequestDistributor(registry, pollCoordinator, executorService, requestQueues);
    executorService.submit(requestDistributor);

    registry.gauge("pooledRequestQueue.enabled", isEnabled, value -> value.get() ? 1.0 : 0.0);
  }

  @PreDestroy
  public void shutdown() {
    requestDistributor.shutdown();
    executorService.shutdown();
    PooledRequest<?> req;
    while ((req = (PooledRequest<?>) submittedRequests.poll()) != null) {
      req.cancel();
    }
  }

  @Override
  public long getDefaultTimeoutMillis() {
    return defaultTimeout;
  }

  @Override
  public long getDefaultStartWorkTimeoutMillis() {
    return defaultStartWorkTimeout;
  }

  @Override
  public <T> T execute(String partition, Callable<T> operation, long startWorkTimeout, long timeout, TimeUnit unit) throws Throwable {
    if (!isEnabled.get()) {
      return operation.call();
    }

    final long startTime = System.nanoTime();
    final Queue<PooledRequest<?>> queue;
    if (!partitionedRequests.containsKey(partition)) {
      Queue<PooledRequest<?>> newQueue = new LinkedBlockingQueue<>();
      Queue<PooledRequest<?>> existing = partitionedRequests.putIfAbsent(partition, newQueue);
      if (existing == null) {
        requestQueues.add(newQueue);
        queue = newQueue;
        registry.gauge(registry.createId("pooledRequestQueue.partition.size", "partition", partition), queue, Queue::size);
      } else {
        queue = existing;
      }
    } else {
      queue = partitionedRequests.get(partition);
    }

    final PooledRequest<T> request = new PooledRequest<>(registry, partition, operation);

    queue.offer(request);
    pollCoordinator.notifyItemsAdded();

    Id id = registry.createId("pooledRequestQueue.totalTime", "partition", partition);
    try {
      T result = request.getPromise().blockingGetOrThrow(startWorkTimeout, timeout, unit);
      id = id.withTag("success", "true");
      return result;
    } catch (Throwable t) {
      id = id.withTags("success", "false", "cause", t.getClass().getSimpleName());
      throw t;
    } finally {
      registry.timer(id).record(System.nanoTime() - startTime, TimeUnit.NANOSECONDS);
    }
  }

  @Scheduled(fixedDelayString = "${requestQueue.corePoolSizeRefreshMs:120000}")
  public void refreshCorePoolSize() {
    int currentCorePoolSize = executorService.getCorePoolSize();
    int desiredCorePoolSize = dynamicConfigService.getConfig(
      Integer.class,
      "requestQueue.poolSize",
      defaultCorePoolSize
    ) + 1;

    if (desiredCorePoolSize != currentCorePoolSize) {
      log.info(
        "Updating core pool size (original: {}, updated: {})",
        currentCorePoolSize,
        desiredCorePoolSize
      );
      executorService.setCorePoolSize(desiredCorePoolSize);
      executorService.setMaximumPoolSize(desiredCorePoolSize);
    }

    isEnabled.set(dynamicConfigService.isEnabled("requestQueue", true));
  }
}
