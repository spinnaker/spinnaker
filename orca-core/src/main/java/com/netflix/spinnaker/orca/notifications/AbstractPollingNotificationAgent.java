/*
 * Copyright 2017 Netflix, Inc.
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
package com.netflix.spinnaker.orca.notifications;

import java.util.function.Function;
import javax.annotation.PreDestroy;
import com.google.common.annotations.VisibleForTesting;
import com.netflix.spinnaker.kork.eureka.RemoteStatusChangedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationListener;
import redis.clients.jedis.Jedis;
import redis.clients.util.Pool;
import rx.Scheduler;
import rx.Subscription;
import rx.schedulers.Schedulers;
import static com.netflix.appinfo.InstanceInfo.InstanceStatus.UP;

abstract public class AbstractPollingNotificationAgent implements ApplicationListener<RemoteStatusChangedEvent> {

  private final Logger log = LoggerFactory.getLogger(AbstractPollingNotificationAgent.class);

  protected final Pool<Jedis> jedisPool;
  protected Scheduler scheduler = Schedulers.io();
  protected Subscription subscription;

  public AbstractPollingNotificationAgent(Pool<Jedis> jedisPool) {
    this.jedisPool = jedisPool;
  }

  protected abstract long getPollingInterval();

  protected abstract String getNotificationType();

  protected abstract void startPolling();

  protected boolean tryAcquireLock() {
    String key = "lock:" + getNotificationType();
    return withJedis(jedisPool, redis ->
      "OK".equals(redis.set(key, "\uD83D\uDD12", "NX", "EX", (long) getPollingInterval())));
  }

  @PreDestroy
  public void stopPolling() {
    if (subscription != null) {
      subscription.unsubscribe();
    }
  }

  @VisibleForTesting
  public void setScheduler(Scheduler scheduler) {
    this.scheduler = scheduler;
  }

  @Override
  public void onApplicationEvent(RemoteStatusChangedEvent event) {
    if (event.getSource().getStatus() == UP) {
      log.info("Instance is UP... starting polling for " + getNotificationType() + " events");
      startPolling();
    } else if (event.getSource().getPreviousStatus() == UP) {
      log.warn("Instance is " + event.getSource().getStatus().toString() +
        "... stopping polling for " + getNotificationType() + " events");
      stopPolling();
    }
  }

  protected <T> T withJedis(Pool<Jedis> jedisPool, Function<Jedis, T> action) {
    try (Jedis jedis = jedisPool.getResource()) {
      return action.apply(jedis);
    }
  }
}
