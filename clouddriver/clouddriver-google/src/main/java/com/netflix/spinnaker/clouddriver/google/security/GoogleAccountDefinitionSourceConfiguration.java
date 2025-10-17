/*
 * Copyright 2025 Harness, Inc.
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

package com.netflix.spinnaker.clouddriver.google.security;

import com.netflix.spinnaker.clouddriver.google.config.GoogleConfigurationProperties;
import com.netflix.spinnaker.clouddriver.security.AccountDefinitionRepository;
import com.netflix.spinnaker.clouddriver.security.AccountDefinitionSource;
import com.netflix.spinnaker.credentials.definition.CredentialsDefinitionSource;
import java.util.List;
import java.util.Optional;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnProperty({"account.storage.enabled", "account.storage.google.enabled"})
public class GoogleAccountDefinitionSourceConfiguration {
  @Bean
  public CredentialsDefinitionSource<GoogleConfigurationProperties.ManagedAccount>
      googleAccountSource(
          AccountDefinitionRepository repository,
          Optional<List<CredentialsDefinitionSource<GoogleConfigurationProperties.ManagedAccount>>>
              additionalSources,
          GoogleConfigurationProperties accountProperties) {
    return new AccountDefinitionSource<>(
        repository,
        GoogleConfigurationProperties.ManagedAccount.class,
        additionalSources.orElseGet(() -> List.of(accountProperties::getAccounts)));
  }
}
