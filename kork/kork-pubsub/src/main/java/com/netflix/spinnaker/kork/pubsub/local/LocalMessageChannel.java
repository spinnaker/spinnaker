/*
 * Copyright 2026 spinnaker.io
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

package com.netflix.spinnaker.kork.pubsub.local;

import com.netflix.spinnaker.kork.pubsub.model.MessageChannel;
import com.netflix.spinnaker.kork.pubsub.model.MessageSubscription;
import com.netflix.spinnaker.kork.pubsub.model.PubsubMessage;
import java.time.Duration;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.function.Consumer;

/** One in-process queue shared by competing subscribers. No Redis classes are needed. */
public class LocalMessageChannel implements MessageChannel {
  private final String name;
  private final LinkedBlockingQueue<PubsubMessage> queue = new LinkedBlockingQueue<>();
  private final Set<Subscription> subscriptions = ConcurrentHashMap.newKeySet();

  public LocalMessageChannel(String name) {
    this.name = name;
  }

  @Override
  public String getPubsubSystem() {
    return "local";
  }

  @Override
  public String getTopicName() {
    return name;
  }

  @Override
  public String getName() {
    return name;
  }

  @Override
  public void publish(String message, Map<String, String> attributes) {
    queue.add(new PubsubMessage(UUID.randomUUID().toString(), message, attributes));
  }

  @Override
  public MessageSubscription subscribe(
      String name,
      int batchSize,
      Consumer<PubsubMessage> handler,
      Consumer<Throwable> errorHandler) {
    if (batchSize < 1) {
      throw new IllegalArgumentException("batchSize must be positive");
    }
    Subscription subscription = new Subscription(name, handler, errorHandler);
    subscriptions.add(subscription);
    return subscription;
  }

  @Override
  public Statistics statistics() {
    long pending =
        subscriptions.stream().mapToLong(subscription -> subscription.pending.size()).sum();
    long consumers = subscriptions.stream().filter(Subscription::isRunning).count();
    return new Statistics(queue.size(), pending, consumers);
  }

  private class Subscription implements MessageSubscription {
    private final String subscriberName;
    private final Consumer<PubsubMessage> handler;
    private final Consumer<Throwable> errorHandler;
    private final Map<String, PubsubMessage> pending = new ConcurrentHashMap<>();
    private volatile boolean running;
    private boolean closed;
    private Thread dispatcher;

    Subscription(String name, Consumer<PubsubMessage> handler, Consumer<Throwable> errorHandler) {
      this.subscriberName = name;
      this.handler = handler;
      this.errorHandler = errorHandler;
    }

    @Override
    public String getPubsubSystem() {
      return "local";
    }

    @Override
    public String getSubscriptionName() {
      return name;
    }

    @Override
    public String getName() {
      return subscriberName;
    }

    @Override
    public boolean isRunning() {
      return running;
    }

    @Override
    public synchronized void start() {
      if (closed) {
        throw new IllegalStateException("Subscription is closed");
      }
      if (running) {
        return;
      }
      running = true;
      dispatcher = new Thread(this::poll, "local-pubsub-" + subscriberName);
      dispatcher.setDaemon(true);
      dispatcher.start();
    }

    private void poll() {
      while (running && Thread.currentThread() == dispatcher) {
        try {
          deliver(queue.take());
        } catch (InterruptedException e) {
          Thread.currentThread().interrupt();
          return;
        }
      }
    }

    private void deliver(PubsubMessage message) {
      synchronized (this) {
        if (!running) {
          queue.add(message);
          return;
        }
        pending.put(message.id(), message);
      }
      try {
        handler.accept(message);
      } catch (Exception e) {
        errorHandler.accept(e);
      }
    }

    @Override
    public synchronized void stop() {
      running = false;
      if (dispatcher != null) {
        dispatcher.interrupt();
      }
    }

    @Override
    public void ack(PubsubMessage message) {
      pending.remove(message.id());
    }

    @Override
    public void nack(PubsubMessage message) {
      PubsubMessage unacknowledged = pending.remove(message.id());
      if (unacknowledged != null) {
        queue.add(unacknowledged);
      }
    }

    @Override
    public long reclaim(Duration minIdle, int limit) {
      return 0;
    }

    @Override
    public synchronized void close() {
      if (closed) {
        return;
      }
      closed = true;
      stop();
      pending.values().forEach(this::nack);
      subscriptions.remove(this);
    }
  }
}
