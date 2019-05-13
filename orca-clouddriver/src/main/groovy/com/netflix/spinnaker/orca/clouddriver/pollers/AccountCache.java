/*
 * Copyright 2019 Netflix, Inc.
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
package com.netflix.spinnaker.orca.clouddriver.pollers;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spectator.api.Counter;
import com.netflix.spectator.api.Registry;
import com.netflix.spinnaker.kork.core.RetrySupport;
import com.netflix.spinnaker.orca.clouddriver.OortService;
import com.netflix.spinnaker.orca.notifications.AbstractPollingNotificationAgent;
import com.netflix.spinnaker.orca.notifications.NotificationClusterLock;
import com.netflix.spinnaker.security.AuthenticatedRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

@Component
@ConditionalOnExpression(value = "${pollers.accountCache.enabled:false}")
public class AccountCache extends AbstractPollingNotificationAgent {
  private final Logger log = LoggerFactory.getLogger(AccountCache.class);

  private final ObjectMapper objectMapper;
  private final OortService oortService;
  private final RetrySupport retrySupport;
  private final Counter successCounter;
  private final Counter errorsCounter;

  private AtomicReference<List<Account>> accounts = new AtomicReference<>();

  @Autowired
  public AccountCache(NotificationClusterLock notificationClusterLock,
                      ObjectMapper objectMapper,
                      OortService oortService,
                      RetrySupport retrySupport, Registry registry) {
    super(notificationClusterLock);
    this.objectMapper = objectMapper;
    this.oortService = oortService;
    this.retrySupport = retrySupport;
    this.successCounter = registry.counter("poller.accounts.success");
    this.errorsCounter = registry.counter("poller.accounts.errors");
  }

  @Override
  protected long getPollingInterval() {
    return TimeUnit.MINUTES.toSeconds(60);
  }

  @Override
  protected TimeUnit getPollingIntervalUnit() {
    return TimeUnit.SECONDS;
  }

  @Override
  protected String getNotificationType() {
    return "accountCache";
  }

  @Override
  protected void tick() {
    refresh();
  }

  private void refresh() {
    try {
      accounts.set(
        AuthenticatedRequest.allowAnonymous(() -> retrySupport.retry(() ->
            objectMapper.convertValue(oortService.getCredentials(true), new TypeReference<List<Account>>() {}),
          5, 3000, false
          )
        ));

      successCounter.increment();
    } catch (Exception e) {
      log.error("Failed to fetch accounts", e);
      errorsCounter.increment();
    }
  }

  private List<Account> getAccounts() {
    if (accounts.get() == null) {
      refresh();
    }

    return accounts.get();
  }

  public String getEnvironment(String accountName) {
    final Optional<Account> account = getAccounts()
      .stream()
      .filter(i -> i.name.equals(accountName))
      .findFirst();

    return account.map(a -> a.environment).orElse("unknown");
  }

  private static class Account {
    public String name;
    public String cloudProvider;
    public String environment;
  }
}
