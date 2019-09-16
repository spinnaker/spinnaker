/*
 * Copyright 2015 Google, Inc.
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

package com.netflix.spinnaker.clouddriver.kubernetes.health;

import com.netflix.spinnaker.clouddriver.kubernetes.security.KubernetesCredentials;
import com.netflix.spinnaker.clouddriver.kubernetes.security.KubernetesNamedAccountCredentials;
import com.netflix.spinnaker.clouddriver.security.AccountCredentialsProvider;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class KubernetesHealthIndicator implements HealthIndicator {
  private final AccountCredentialsProvider accountCredentialsProvider;
  private final AtomicReference<Map<String, String>> warningMessages = new AtomicReference<>(null);

  @Autowired
  public KubernetesHealthIndicator(AccountCredentialsProvider accountCredentialsProvider) {
    this.accountCredentialsProvider = accountCredentialsProvider;
  }

  @Override
  public Health health() {
    Map<String, String> warnings = warningMessages.get();

    Health.Builder resultBuilder = new Health.Builder().up();
    warnings.forEach(resultBuilder::withDetail);

    return resultBuilder.build();
  }

  @Scheduled(fixedDelay = 300000L)
  public void checkHealth() {
    Map<String, String> warnings = new HashMap<>();

    Set<KubernetesNamedAccountCredentials> kubernetesCredentialsSet =
        accountCredentialsProvider.getAll().stream()
            .filter(a -> a instanceof KubernetesNamedAccountCredentials)
            .map(a -> (KubernetesNamedAccountCredentials) a)
            .collect(Collectors.toSet());

    for (KubernetesNamedAccountCredentials accountCredentials : kubernetesCredentialsSet) {
      try {
        KubernetesCredentials kubernetesCredentials = accountCredentials.getCredentials();
        kubernetesCredentials.getDeclaredNamespaces();
      } catch (Exception e) {
        String accountName = String.format("kubernetes:%s", accountCredentials.getName());
        warnings.put(accountName, e.getMessage());
      }
    }

    warningMessages.set(warnings);
  }
}
