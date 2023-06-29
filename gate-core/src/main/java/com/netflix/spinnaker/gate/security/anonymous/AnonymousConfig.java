/*
 * Copyright 2015 Netflix, Inc.
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

package com.netflix.spinnaker.gate.security.anonymous;

import com.netflix.spinnaker.fiat.shared.FiatStatus;
import com.netflix.spinnaker.gate.security.SpinnakerAuthConfig;
import com.netflix.spinnaker.gate.services.CredentialsService;
import com.netflix.spinnaker.security.User;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;
import org.springframework.util.CollectionUtils;

/**
 * Requires auth.anonymous.enabled to be true in Fiat configs to work properly. This is because
 * anonymous users are a special permissions case, because the "user" doesn't actually exist in the
 * backing UserRolesProvider.
 */
@ConditionalOnMissingBean(annotation = SpinnakerAuthConfig.class)
@Configuration
@Log4j2
@EnableWebSecurity
@Order(Ordered.LOWEST_PRECEDENCE)
@RequiredArgsConstructor
public class AnonymousConfig extends WebSecurityConfigurerAdapter {
  private static final String key = "spinnaker-anonymous";
  private static final String defaultEmail = "anonymous";

  private final CredentialsService credentialsService;
  private final FiatStatus fiatStatus;
  @Getter private final List<String> anonymousAllowedAccounts = new CopyOnWriteArrayList<>();

  @Override
  @SuppressWarnings("deprecation")
  public void configure(HttpSecurity http) throws Exception {
    updateAnonymousAccounts();
    // Not using the ImmutableUser version in order to update allowedAccounts.
    User principal = new User();
    principal.setEmail(defaultEmail);
    principal.setAllowedAccounts(anonymousAllowedAccounts);

    http.anonymous().key(key).principal(principal).and().csrf().disable();
  }

  @Scheduled(fixedDelay = 60000L)
  public void updateAnonymousAccounts() {
    if (fiatStatus.isEnabled()) {
      return;
    }

    try {
      Collection<String> names = credentialsService.getAccountNames(Set.of());
      Collection<String> newAnonAccounts = !CollectionUtils.isEmpty(names) ? names : Set.of();

      var toAdd = new HashSet<>(newAnonAccounts);
      anonymousAllowedAccounts.forEach(toAdd::remove);
      var toRemove = new HashSet<>(anonymousAllowedAccounts);
      newAnonAccounts.forEach(toRemove::remove);

      anonymousAllowedAccounts.removeAll(toRemove);
      anonymousAllowedAccounts.addAll(toAdd);
    } catch (Exception e) {
      log.warn("Error while updating anonymous accounts", e);
    }
  }
}
