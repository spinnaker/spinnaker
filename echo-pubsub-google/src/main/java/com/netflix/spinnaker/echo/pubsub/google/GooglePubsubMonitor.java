/*
 * Copyright 2017 Google, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.echo.pubsub.google;

import com.netflix.spinnaker.echo.model.pubsub.PubsubSystem;
import com.netflix.spinnaker.echo.pubsub.PollingMonitor;
import com.netflix.spinnaker.echo.pubsub.PubsubSubscribers;
import com.netflix.spinnaker.echo.pubsub.model.PubsubSubscriber;
import javax.annotation.PreDestroy;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/** Monitors Google Cloud Pubsub subscriptions. */
@Slf4j
@Service
@Async
@ConditionalOnExpression("${pubsub.enabled:false} && ${pubsub.google.enabled:false}")
public class GooglePubsubMonitor implements PollingMonitor {

  private Long lastPoll;

  @Getter private final String name = "GooglePubsubMonitor";

  @Autowired private PubsubSubscribers pubsubSubscribers;

  @PreDestroy
  private void closeAsyncConnections() {
    log.info("Closing async connections for Google Pubsub subscribers");
    pubsubSubscribers
        .subscribersMatchingType(PubsubSystem.GOOGLE)
        .parallelStream()
        .forEach(this::closeConnection);
  }

  @Override
  public void onApplicationEvent(ContextRefreshedEvent event) {
    // TODO(jacobkiefer): Register Echo as enabled on startup.
    log.info("Starting async connections for Google Pubsub subscribers");
    pubsubSubscribers
        .subscribersMatchingType(PubsubSystem.GOOGLE)
        .parallelStream()
        .forEach(this::openConnection);
  }

  private void openConnection(PubsubSubscriber subscriber) {
    log.info("Opening async connection to {}", subscriber.getSubscriptionName());
    lastPoll = System.currentTimeMillis();

    GooglePubsubSubscriber googleSubscriber = (GooglePubsubSubscriber) subscriber;
    googleSubscriber.start();
  }

  private void closeConnection(PubsubSubscriber subscriber) {
    log.info("Closing async connection to {}", subscriber.getSubscriptionName());
    GooglePubsubSubscriber googleSubscriber = (GooglePubsubSubscriber) subscriber;
    googleSubscriber.stop();
  }

  @Override
  public boolean isInService() {
    return true;
  }

  @Override
  public Long getLastPoll() {
    return lastPoll;
  }

  @Override
  public int getPollInterval() {
    return -1;
  }
}
