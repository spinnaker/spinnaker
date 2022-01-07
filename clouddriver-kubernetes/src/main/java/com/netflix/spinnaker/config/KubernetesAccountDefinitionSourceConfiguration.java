/*
 * Copyright 2021 Apple Inc.
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

package com.netflix.spinnaker.config;

import com.netflix.spinnaker.clouddriver.kubernetes.config.KubernetesAccountProperties;
import com.netflix.spinnaker.clouddriver.security.AccountDefinitionRepository;
import com.netflix.spinnaker.clouddriver.security.AccountDefinitionSource;
import com.netflix.spinnaker.credentials.definition.CredentialsDefinitionSource;
import java.util.List;
import java.util.Optional;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

@Configuration
@ConditionalOnProperty("account.storage.enabled")
public class KubernetesAccountDefinitionSourceConfiguration {
  @Bean
  @Primary
  public CredentialsDefinitionSource<KubernetesAccountProperties.ManagedAccount>
      kubernetesAccountSource(
          AccountDefinitionRepository repository,
          Optional<List<CredentialsDefinitionSource<KubernetesAccountProperties.ManagedAccount>>>
              additionalSources,
          KubernetesAccountProperties accountProperties) {
    return new AccountDefinitionSource<>(
        repository,
        KubernetesAccountProperties.ManagedAccount.class,
        additionalSources.orElseGet(() -> List.of(accountProperties::getAccounts)));
  }
}
