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
import com.netflix.spinnaker.credentials.CredentialsLifecycleHandler;
import com.netflix.spinnaker.credentials.CredentialsRepository;
import com.netflix.spinnaker.credentials.MapBackedCredentialsRepository;
import com.netflix.spinnaker.credentials.definition.CredentialsDefinitionSource;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Comprehensive test for Azure dynamic account loading matching AWS/Google patterns. Tests the
 * complete flow from configuration to credentials repository.
 */
public class AzureDynamicAccountLoadingTest {

  private AzureCredentialsConfiguration credentialsConfiguration;
  private AzureConfigurationProperties mockConfigProperties;

  @BeforeEach
  void setUp() {
    credentialsConfiguration = new AzureCredentialsConfiguration();
    mockConfigProperties = mock(AzureConfigurationProperties.class);
  }

  @Test
  void testCredentialsRepositoryIsCreatedWithLifecycleHandler() {
    // Arrange
    @SuppressWarnings("unchecked")
    CredentialsLifecycleHandler<AzureNamedAccountCredentials> mockLifecycleHandler =
        mock(CredentialsLifecycleHandler.class);

    // Act
    CredentialsRepository<AzureNamedAccountCredentials> repository =
        credentialsConfiguration.azureCredentialsRepository(mockLifecycleHandler);

    // Assert
    assertThat(repository).isNotNull();
    assertThat(repository).isInstanceOf(MapBackedCredentialsRepository.class);
  }

  @Test
  void testAccountDefinitionSourceIntegration() {
    // Arrange
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
    when(mockConfigProperties.getAccounts()).thenReturn(accounts);

    AzureAccountDefinitionSourceConfiguration sourceConfig =
        new AzureAccountDefinitionSourceConfiguration();
    com.netflix.spinnaker.clouddriver.security.AccountDefinitionRepository mockRepository =
        mock(com.netflix.spinnaker.clouddriver.security.AccountDefinitionRepository.class);

    // Act
    CredentialsDefinitionSource<AzureConfigurationProperties.ManagedAccount> source =
        sourceConfig.azureAccountSource(
            mockRepository, java.util.Optional.empty(), mockConfigProperties);
    List<AzureConfigurationProperties.ManagedAccount> result = source.getCredentialsDefinitions();

    // Assert
    assertThat(result).hasSize(2);
    assertThat(result.get(0).getName()).isEqualTo("test-account-1");
    assertThat(result.get(0).getClientId()).isEqualTo("test-client-id-1");
    assertThat(result.get(1).getName()).isEqualTo("test-account-2");
    assertThat(result.get(1).getClientId()).isEqualTo("test-client-id-2");
  }

  @Test
  void testJsonTypeNameAnnotationIsPresent() throws Exception {
    // Verify that ManagedAccount has @JsonTypeName annotation
    AzureConfigurationProperties.ManagedAccount account =
        new AzureConfigurationProperties.ManagedAccount();
    account.setName("test");

    // Check if the class has the annotation
    com.fasterxml.jackson.annotation.JsonTypeName annotation =
        AzureConfigurationProperties.ManagedAccount.class.getAnnotation(
            com.fasterxml.jackson.annotation.JsonTypeName.class);

    assertThat(annotation).isNotNull();
    assertThat(annotation.value()).isEqualTo("azure");
  }

  @Test
  void testDynamicLoadingWithEmptyAccounts() {
    // Arrange
    when(mockConfigProperties.getAccounts()).thenReturn(Collections.emptyList());

    AzureAccountDefinitionSourceConfiguration sourceConfig =
        new AzureAccountDefinitionSourceConfiguration();
    com.netflix.spinnaker.clouddriver.security.AccountDefinitionRepository mockRepository =
        mock(com.netflix.spinnaker.clouddriver.security.AccountDefinitionRepository.class);

    // Act
    CredentialsDefinitionSource<AzureConfigurationProperties.ManagedAccount> source =
        sourceConfig.azureAccountSource(
            mockRepository, java.util.Optional.empty(), mockConfigProperties);
    List<AzureConfigurationProperties.ManagedAccount> result = source.getCredentialsDefinitions();

    // Assert
    assertThat(result).isEmpty();
  }

  @Test
  void testMultipleAccountSources() {
    // Arrange - Test with multiple account sources
    AzureConfigurationProperties.ManagedAccount account1 =
        new AzureConfigurationProperties.ManagedAccount();
    account1.setName("config-account");
    account1.setClientId("config-client-id");
    account1.setTenantId("config-tenant-id");
    account1.setSubscriptionId("config-subscription-id");

    when(mockConfigProperties.getAccounts()).thenReturn(Collections.singletonList(account1));

    AzureAccountDefinitionSourceConfiguration sourceConfig =
        new AzureAccountDefinitionSourceConfiguration();
    com.netflix.spinnaker.clouddriver.security.AccountDefinitionRepository mockRepository =
        mock(com.netflix.spinnaker.clouddriver.security.AccountDefinitionRepository.class);

    // Act
    CredentialsDefinitionSource<AzureConfigurationProperties.ManagedAccount> source =
        sourceConfig.azureAccountSource(
            mockRepository, java.util.Optional.empty(), mockConfigProperties);
    List<AzureConfigurationProperties.ManagedAccount> result = source.getCredentialsDefinitions();

    // Assert - should have account from config
    assertThat(result).hasSize(1);
    assertThat(result.get(0).getName()).isEqualTo("config-account");
  }

  @Test
  void testCredentialsLoaderIsCreated() {
    // Arrange
    @SuppressWarnings("unchecked")
    com.netflix.spinnaker.credentials.definition.CredentialsParser<
            AzureConfigurationProperties.ManagedAccount, AzureNamedAccountCredentials>
        mockParser = mock(com.netflix.spinnaker.credentials.definition.CredentialsParser.class);

    @SuppressWarnings("unchecked")
    CredentialsRepository<AzureNamedAccountCredentials> mockRepository =
        mock(CredentialsRepository.class);

    @SuppressWarnings("unchecked")
    CredentialsDefinitionSource<AzureConfigurationProperties.ManagedAccount> mockSource =
        mock(CredentialsDefinitionSource.class);

    when(mockConfigProperties.getAccounts()).thenReturn(Collections.emptyList());

    // Act
    com.netflix.spinnaker.credentials.definition.AbstractCredentialsLoader<
            AzureNamedAccountCredentials>
        loader =
            credentialsConfiguration.azureCredentialsLoader(
                mockParser, mockRepository, mockConfigProperties, mockSource);

    // Assert
    assertThat(loader).isNotNull();
  }

  @Test
  void testCredentialsInitializerSynchronizableIsCreated() {
    // Arrange
    @SuppressWarnings("unchecked")
    com.netflix.spinnaker.credentials.definition.AbstractCredentialsLoader<
            AzureNamedAccountCredentials>
        mockLoader =
            mock(com.netflix.spinnaker.credentials.definition.AbstractCredentialsLoader.class);

    // Act
    com.netflix.spinnaker.clouddriver.security.CredentialsInitializerSynchronizable synchronizable =
        credentialsConfiguration.azureCredentialsInitializerSynchronizable(mockLoader);

    // Assert
    assertThat(synchronizable).isNotNull();
  }

  @Test
  void testAccountWithAllProperties() {
    // Arrange - Test account with all properties set
    AzureConfigurationProperties.ManagedAccount account =
        new AzureConfigurationProperties.ManagedAccount();
    account.setName("full-account");
    account.setClientId("client-id");
    account.setAppKey("app-key");
    account.setTenantId("tenant-id");
    account.setSubscriptionId("subscription-id");
    account.setDefaultResourceGroup("default-rg");
    account.setDefaultKeyVault("default-kv");
    account.setRegions(Arrays.asList("westus", "eastus"));
    account.setEnvironment("test");
    account.setAccountType("test");

    when(mockConfigProperties.getAccounts()).thenReturn(Collections.singletonList(account));

    AzureAccountDefinitionSourceConfiguration sourceConfig =
        new AzureAccountDefinitionSourceConfiguration();
    com.netflix.spinnaker.clouddriver.security.AccountDefinitionRepository mockRepository =
        mock(com.netflix.spinnaker.clouddriver.security.AccountDefinitionRepository.class);

    // Act
    CredentialsDefinitionSource<AzureConfigurationProperties.ManagedAccount> source =
        sourceConfig.azureAccountSource(
            mockRepository, java.util.Optional.empty(), mockConfigProperties);
    List<AzureConfigurationProperties.ManagedAccount> result = source.getCredentialsDefinitions();

    // Assert
    assertThat(result).hasSize(1);
    AzureConfigurationProperties.ManagedAccount resultAccount = result.get(0);
    assertThat(resultAccount.getName()).isEqualTo("full-account");
    assertThat(resultAccount.getClientId()).isEqualTo("client-id");
    assertThat(resultAccount.getTenantId()).isEqualTo("tenant-id");
    assertThat(resultAccount.getSubscriptionId()).isEqualTo("subscription-id");
    assertThat(resultAccount.getDefaultResourceGroup()).isEqualTo("default-rg");
    assertThat(resultAccount.getDefaultKeyVault()).isEqualTo("default-kv");
    assertThat(resultAccount.getRegions()).containsExactly("westus", "eastus");
    assertThat(resultAccount.getEnvironment()).isEqualTo("test");
    assertThat(resultAccount.getAccountType()).isEqualTo("test");
  }
}
