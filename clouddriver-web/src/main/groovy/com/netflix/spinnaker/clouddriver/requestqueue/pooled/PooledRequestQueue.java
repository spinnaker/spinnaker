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

import com.netflix.spectator.api.NoopRegistry;
import com.netflix.spectator.api.Registry;
import com.netflix.spinnaker.clouddriver.requestqueue.RequestQueue;

import javax.annotation.PreDestroy;
import java.util.Collection;
import java.util.Queue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class PooledRequestQueue implements RequestQueue {
  private final ConcurrentMap<String, Queue<PooledRequest<?>>> partitionedRequests = new ConcurrentHashMap<>();
  private final PollCoordinator pollCoordinator = new PollCoordinator();

  private final long defaultTimeout;
  private final ExecutorService executorService;
  private final BlockingQueue<Runnable> submittedRequests;
  private final Collection<Queue<PooledRequest<?>>> requestQueues;
  private final RequestDistributor requestDistributor;
  private final Registry registry;

  public PooledRequestQueue(Registry registry, long defaultTimeout, int requestPoolSize) {
    if (defaultTimeout <= 0) {
      throw new IllegalArgumentException("defaultTimeout");
    }

    if (requestPoolSize < 1) {
      throw new IllegalArgumentException("requestPoolSize");
    }
    this.registry = registry;
    this.defaultTimeout = defaultTimeout;
    this.submittedRequests = new LinkedBlockingQueue<>();
    registry.gauge("pooledRequestQueue.executorQueue.size", submittedRequests, Queue::size);
    final int actualThreads = requestPoolSize + 1;
    this.executorService = new ThreadPoolExecutor(actualThreads, actualThreads, 0, TimeUnit.MILLISECONDS, submittedRequests);
    this.requestQueues = new CopyOnWriteArrayList<>();
    this.requestDistributor = new RequestDistributor(registry, pollCoordinator, executorService, requestQueues);
    executorService.submit(requestDistributor);
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
  public <T> T execute(String partition, Callable<T> operation, long timeout, TimeUnit unit) throws Throwable {
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

    return request.getPromise().blockingGetOrThrow(timeout, unit);
  }
}
