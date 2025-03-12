/*
 * Copyright 2015 Google, Inc.
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
 *
 */

package com.netflix.spinnaker.clouddriver.kubernetes.health;

import com.google.common.collect.ImmutableList;
import com.netflix.spectator.api.Registry;
import com.netflix.spinnaker.clouddriver.core.AccountHealthIndicator;
import com.netflix.spinnaker.clouddriver.kubernetes.config.KubernetesConfigurationProperties;
import com.netflix.spinnaker.clouddriver.kubernetes.security.KubernetesNamedAccountCredentials;
import com.netflix.spinnaker.credentials.CredentialsRepository;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;

@Slf4j
public class KubernetesHealthIndicator
    extends AccountHealthIndicator<KubernetesNamedAccountCredentials> {
  private static final String ID = "kubernetes";
  private final CredentialsRepository<KubernetesNamedAccountCredentials> credentialsRepository;
  private final KubernetesConfigurationProperties kubernetesConfigurationProperties;

  @Autowired
  public KubernetesHealthIndicator(
      Registry registry,
      CredentialsRepository<KubernetesNamedAccountCredentials> credentialsRepository,
      KubernetesConfigurationProperties kubernetesConfigurationProperties) {
    super(ID, registry);
    this.credentialsRepository = credentialsRepository;
    this.kubernetesConfigurationProperties = kubernetesConfigurationProperties;

    if (kubernetesConfigurationProperties.isVerifyAccountHealth()) {
      log.info(
          "kubernetes.verifyAccountHealth flag is enabled - declared namespaces will be retrieved for all accounts");
    } else {
      log.warn(
          "kubernetes.verifyAccountHealth flag is disabled - declared namespaces will not be retrieved for any account");
    }
  }

  @Override
  protected ImmutableList<KubernetesNamedAccountCredentials> getAccounts() {
    return ImmutableList.copyOf(credentialsRepository.getAll());
  }

  @Override
  protected Optional<String> accountHealth(KubernetesNamedAccountCredentials accountCredentials) {
    if (kubernetesConfigurationProperties.isVerifyAccountHealth()) {
      try {
        accountCredentials.getCredentials().getDeclaredNamespaces();
      } catch (RuntimeException e) {
        return Optional.of(e.getMessage());
      }
    }
    return Optional.empty();
  }
}
