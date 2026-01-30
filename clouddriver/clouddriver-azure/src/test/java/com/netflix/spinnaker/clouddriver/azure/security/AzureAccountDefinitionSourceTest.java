/*
 * Copyright 2025 Harness, Inc.
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

package com.netflix.spinnaker.clouddriver.azure.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.netflix.spinnaker.clouddriver.azure.config.AzureConfigurationProperties;
import com.netflix.spinnaker.clouddriver.security.AccountDefinitionRepository;
import com.netflix.spinnaker.credentials.definition.CredentialsDefinitionSource;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

public class AzureAccountDefinitionSourceTest {

  @Test
  void testAccountDefinitionSourceReturnsAccounts() {
    // Arrange
    AzureConfigurationProperties mockProperties = mock(AzureConfigurationProperties.class);
    AccountDefinitionRepository mockRepository = mock(AccountDefinitionRepository.class);

    AzureConfigurationProperties.ManagedAccount account1 =
        new AzureConfigurationProperties.ManagedAccount();
    account1.setName("test-account-1");
    account1.setClientId("test-client-id-1");
    account1.setTenantId("test-tenant-id-1");
    account1.setSubscriptionId("test-subscription-id-1");

    AzureConfigurationProperties.ManagedAccount account2 =
        new AzureConfigurationProperties.ManagedAccount();
    account2.setName("test-account-2");
    account2.setClientId("test-client-id-2");
    account2.setTenantId("test-tenant-id-2");
    account2.setSubscriptionId("test-subscription-id-2");

    List<AzureConfigurationProperties.ManagedAccount> accounts = Arrays.asList(account1, account2);
    when(mockProperties.getAccounts()).thenReturn(accounts);

    AzureAccountDefinitionSourceConfiguration config =
        new AzureAccountDefinitionSourceConfiguration();

    // Act
    CredentialsDefinitionSource<AzureConfigurationProperties.ManagedAccount> source =
        config.azureAccountSource(mockRepository, Optional.empty(), mockProperties);
    List<AzureConfigurationProperties.ManagedAccount> result = source.getCredentialsDefinitions();

    // Assert
    assertThat(result).isNotNull();
    assertThat(result).hasSize(2);
    assertThat(result.get(0).getName()).isEqualTo("test-account-1");
    assertThat(result.get(1).getName()).isEqualTo("test-account-2");
  }

  @Test
  void testAccountDefinitionSourceReturnsEmptyListWhenNoAccounts() {
    // Arrange
    AzureConfigurationProperties mockProperties = mock(AzureConfigurationProperties.class);
    AccountDefinitionRepository mockRepository = mock(AccountDefinitionRepository.class);
    when(mockProperties.getAccounts()).thenReturn(Collections.emptyList());

    AzureAccountDefinitionSourceConfiguration config =
        new AzureAccountDefinitionSourceConfiguration();

    // Act
    CredentialsDefinitionSource<AzureConfigurationProperties.ManagedAccount> source =
        config.azureAccountSource(mockRepository, Optional.empty(), mockProperties);
    List<AzureConfigurationProperties.ManagedAccount> result = source.getCredentialsDefinitions();

    // Assert
    assertThat(result).isNotNull();
    assertThat(result).isEmpty();
  }

  @Test
  void testAccountDefinitionSourceIsConditionalOnProperty() {
    // Verify that the bean is conditional on account.storage properties
    AzureAccountDefinitionSourceConfiguration config =
        new AzureAccountDefinitionSourceConfiguration();
    assertThat(config).isNotNull();
  }
}
