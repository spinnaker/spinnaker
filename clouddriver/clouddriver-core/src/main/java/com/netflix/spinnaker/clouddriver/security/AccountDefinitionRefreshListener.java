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

package com.netflix.spinnaker.clouddriver.security;

import com.netflix.spinnaker.credentials.Credentials;
import com.netflix.spinnaker.credentials.definition.CredentialsLoader;
import com.netflix.spinnaker.kork.pubsub.model.MessageChannel;
import com.netflix.spinnaker.kork.pubsub.model.MessageSubscription;
import com.netflix.spinnaker.kork.pubsub.model.PubsubMessage;
import java.time.Duration;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.scheduling.annotation.Scheduled;

/**
 * Every replica reloads from its authoritative sources; notifications never contain definitions.
 */
public class AccountDefinitionRefreshListener
    implements ApplicationListener<ApplicationReadyEvent>, DisposableBean {
  private static final Logger LOGGER = LogManager.getLogger();
  private final MessageChannel channel;
  private final ObjectProvider<CredentialsLoader<? extends Credentials>> loaders;
  private final Duration reclaimIdle;
  private final long maxLength;
  private final MessageSubscription subscription;
  private boolean started;

  public AccountDefinitionRefreshListener(
      MessageChannel channel,
      ObjectProvider<CredentialsLoader<? extends Credentials>> loaders,
      long reclaimIdleMs,
      long maxLength) {
    if (reclaimIdleMs < 0 || maxLength < 1) {
      throw new IllegalArgumentException(
          "Account pubsub idle threshold must be nonnegative and stream length positive");
    }
    this.channel = channel;
    this.loaders = loaders;
    this.reclaimIdle = Duration.ofMillis(reclaimIdleMs);
    this.maxLength = maxLength;
    subscription =
        channel.subscribe(
            "account-refresh",
            1,
            this::refresh,
            error ->
                LOGGER.warn("Account refresh failed; the notification will be retried", error));
  }

  @Override
  public synchronized void onApplicationEvent(ApplicationReadyEvent event) {
    if (started) {
      return;
    }
    subscription.start();
    // Close the gap between @PostConstruct loading and creation of the broadcast subscription.
    reload();
    started = true;
  }

  private synchronized void refresh(PubsubMessage message) {
    reload();
    subscription.ack(message);
  }

  private void reload() {
    RuntimeException failure = null;
    for (CredentialsLoader<? extends Credentials> loader : loaders) {
      try {
        loader.load();
      } catch (RuntimeException e) {
        if (failure == null) {
          failure = e;
        } else {
          failure.addSuppressed(e);
        }
      }
    }
    if (failure != null) {
      throw failure;
    }
  }

  @Scheduled(fixedDelayString = "${account.storage.pubsub.maintenance-interval-ms:60000}")
  public void maintain() {
    if (!subscription.isRunning()) {
      return;
    }
    try {
      subscription.reclaim(reclaimIdle, 100);
      channel.trim(maxLength);
    } catch (RuntimeException e) {
      LOGGER.warn("Unable to maintain account notification subscription", e);
    }
  }

  @Override
  public void destroy() {
    subscription.close();
  }
}
