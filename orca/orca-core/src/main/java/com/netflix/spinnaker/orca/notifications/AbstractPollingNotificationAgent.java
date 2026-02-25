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

import static com.netflix.spinnaker.kork.discovery.InstanceStatus.UP;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.netflix.spinnaker.kork.discovery.RemoteStatusChangedEvent;
import jakarta.annotation.PreDestroy;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.context.ApplicationListener;

public abstract class AbstractPollingNotificationAgent
    implements ApplicationListener<RemoteStatusChangedEvent> {

  private final Logger log = LoggerFactory.getLogger(AbstractPollingNotificationAgent.class);
  private ScheduledExecutorService executorService = null;
  private ScheduledFuture agentRunFuture = null;

  protected final NotificationClusterLock clusterLock;

  public static final String AGENT_MDC_KEY = "agentClass";

  public AbstractPollingNotificationAgent(NotificationClusterLock clusterLock) {
    this.clusterLock = clusterLock;
  }

  protected abstract long getPollingInterval();

  protected TimeUnit getPollingIntervalUnit() {
    return TimeUnit.MILLISECONDS;
  }

  protected abstract String getNotificationType();

  protected abstract void tick();

  protected void startPolling() {
    Runnable agentTickWrapper =
        () -> {
          try {
            MDC.put(AGENT_MDC_KEY, this.getClass().getSimpleName());
            if (tryAcquireLock()) {
              tick();
            }
          } catch (Exception e) {
            log.error("Error running agent tick", e);
          } finally {
            MDC.remove(AGENT_MDC_KEY);
          }
        };

    if (agentRunFuture == null) {
      executorService =
          Executors.newSingleThreadScheduledExecutor(
              new ThreadFactoryBuilder()
                  .setNameFormat(this.getClass().getSimpleName() + "-%d")
                  .build());
      agentRunFuture =
          executorService.scheduleWithFixedDelay(
              agentTickWrapper, 0, getPollingInterval(), getPollingIntervalUnit());
    } else {
      log.warn("Not starting polling on {} because it's already running", getNotificationType());
    }
  }

  protected boolean tryAcquireLock() {
    return clusterLock.tryAcquireLock(
        getNotificationType(), getPollingIntervalUnit().toSeconds(getPollingInterval()));
  }

  @PreDestroy
  public void stopPolling() {
    if (agentRunFuture != null) {
      agentRunFuture.cancel(true);
      agentRunFuture = null;
    }
    if (executorService != null) {
      executorService.shutdown();
      executorService = null;
    }
  }

  @Override
  public void onApplicationEvent(RemoteStatusChangedEvent event) {
    if (event.getSource().getStatus() == UP) {
      log.info("Instance is UP... starting polling for " + getNotificationType() + " events");
      startPolling();
    } else if (event.getSource().getPreviousStatus() == UP) {
      log.warn(
          "Instance is "
              + event.getSource().getStatus().toString()
              + "... stopping polling for "
              + getNotificationType()
              + " events");
      stopPolling();
    }
  }
}
