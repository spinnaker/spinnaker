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

package com.netflix.spinnaker.clouddriver.google.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spectator.api.Registry;
import com.netflix.spinnaker.clouddriver.google.GoogleExecutor;
import com.netflix.spinnaker.clouddriver.google.config.GoogleConfigurationProperties;
import com.netflix.spinnaker.clouddriver.google.config.GoogleCredentialsConfiguration;
import com.netflix.spinnaker.clouddriver.names.NamerRegistry;
import com.netflix.spinnaker.config.GoogleConfiguration;
import com.netflix.spinnaker.credentials.CredentialsLifecycleHandler;
import com.netflix.spinnaker.credentials.CredentialsRepository;
import com.netflix.spinnaker.credentials.CredentialsTypeBaseConfiguration;
import com.netflix.spinnaker.credentials.MapBackedCredentialsRepository;
import com.netflix.spinnaker.credentials.definition.CredentialsDefinitionSource;
import com.netflix.spinnaker.kork.client.ServiceClientProvider;
import com.netflix.spinnaker.kork.configserver.ConfigFileService;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationContext;

/**
 * Comprehensive test for Google dynamic account loading matching AWS patterns. Tests the complete
 * flow from configuration to credentials repository.
 */
public class GoogleDynamicAccountLoadingTest {

  private GoogleCredentialsConfiguration credentialsConfiguration;
  private ApplicationContext mockApplicationContext;
  private GoogleConfigurationProperties mockConfigProperties;
  private ConfigFileService mockConfigFileService;
  private GoogleConfiguration.DeployDefaults mockDeployDefaults;
  private GoogleExecutor mockGoogleExecutor;
  private NamerRegistry mockNamerRegistry;
  private ServiceClientProvider mockServiceClientProvider;
  private Registry mockRegistry;
  private ObjectMapper objectMapper;

  @BeforeEach
  void setUp() {
    credentialsConfiguration = new GoogleCredentialsConfiguration();
    mockApplicationContext = mock(ApplicationContext.class);
    mockConfigProperties = mock(GoogleConfigurationProperties.class);
    mockConfigFileService = mock(ConfigFileService.class);
    mockDeployDefaults = mock(GoogleConfiguration.DeployDefaults.class);
    mockGoogleExecutor = mock(GoogleExecutor.class);
    mockNamerRegistry = mock(NamerRegistry.class);
    mockServiceClientProvider = mock(ServiceClientProvider.class);
    mockRegistry = mock(Registry.class);
    objectMapper = new ObjectMapper();
  }

  @Test
  void testCredentialsTypeBaseConfigurationIsCreated() {
    // Arrange
    when(mockConfigProperties.getAccounts()).thenReturn(Collections.emptyList());
    when(mockConfigFileService.getContents(any())).thenReturn("{}");

    // Act
    CredentialsTypeBaseConfiguration<
            GoogleNamedAccountCredentials, GoogleConfigurationProperties.ManagedAccount>
        config =
            credentialsConfiguration.googleCredentialsProperties(
                mockApplicationContext,
                mockConfigProperties,
                mockConfigFileService,
                mockDeployDefaults,
                mockGoogleExecutor,
                "test-user-agent");

    // Assert
    assertThat(config).isNotNull();
  }

  @Test
  void testCredentialsRepositoryIsCreatedWithLifecycleHandler() {
    // Arrange
    @SuppressWarnings("unchecked")
    CredentialsLifecycleHandler<GoogleNamedAccountCredentials> mockLifecycleHandler =
        mock(CredentialsLifecycleHandler.class);

    // Act
    CredentialsRepository<GoogleNamedAccountCredentials> repository =
        credentialsConfiguration.googleCredentialsRepository(mockLifecycleHandler);

    // Assert
    assertThat(repository).isNotNull();
    assertThat(repository).isInstanceOf(MapBackedCredentialsRepository.class);
  }

  @Test
  void testAccountDefinitionSourceIntegration() {
    // Arrange
    GoogleConfigurationProperties.ManagedAccount account1 =
        new GoogleConfigurationProperties.ManagedAccount();
    account1.setName("test-account-1");
    account1.setProject("test-project-1");

    GoogleConfigurationProperties.ManagedAccount account2 =
        new GoogleConfigurationProperties.ManagedAccount();
    account2.setName("test-account-2");
    account2.setProject("test-project-2");

    List<GoogleConfigurationProperties.ManagedAccount> accounts = Arrays.asList(account1, account2);
    when(mockConfigProperties.getAccounts()).thenReturn(accounts);

    GoogleAccountDefinitionSourceConfiguration sourceConfig =
        new GoogleAccountDefinitionSourceConfiguration();
    com.netflix.spinnaker.clouddriver.security.AccountDefinitionRepository mockRepository =
        mock(com.netflix.spinnaker.clouddriver.security.AccountDefinitionRepository.class);

    // Act
    CredentialsDefinitionSource<GoogleConfigurationProperties.ManagedAccount> source =
        sourceConfig.googleAccountSource(
            mockRepository, java.util.Optional.empty(), mockConfigProperties);
    List<GoogleConfigurationProperties.ManagedAccount> result = source.getCredentialsDefinitions();

    // Assert
    assertThat(result).hasSize(2);
    assertThat(result.get(0).getName()).isEqualTo("test-account-1");
    assertThat(result.get(0).getProject()).isEqualTo("test-project-1");
    assertThat(result.get(1).getName()).isEqualTo("test-account-2");
    assertThat(result.get(1).getProject()).isEqualTo("test-project-2");
  }

  @Test
  void testJsonTypeNameAnnotationIsPresent() throws Exception {
    // Verify that ManagedAccount has @JsonTypeName annotation
    GoogleConfigurationProperties.ManagedAccount account =
        new GoogleConfigurationProperties.ManagedAccount();
    account.setName("test");

    // Check if the class has the annotation
    com.fasterxml.jackson.annotation.JsonTypeName annotation =
        GoogleConfigurationProperties.ManagedAccount.class.getAnnotation(
            com.fasterxml.jackson.annotation.JsonTypeName.class);

    assertThat(annotation).isNotNull();
    assertThat(annotation.value()).isEqualTo("google");
  }

  @Test
  void testDynamicLoadingWithEmptyAccounts() {
    // Arrange
    when(mockConfigProperties.getAccounts()).thenReturn(Collections.emptyList());

    GoogleAccountDefinitionSourceConfiguration sourceConfig =
        new GoogleAccountDefinitionSourceConfiguration();
    com.netflix.spinnaker.clouddriver.security.AccountDefinitionRepository mockRepository =
        mock(com.netflix.spinnaker.clouddriver.security.AccountDefinitionRepository.class);

    // Act
    CredentialsDefinitionSource<GoogleConfigurationProperties.ManagedAccount> source =
        sourceConfig.googleAccountSource(
            mockRepository, java.util.Optional.empty(), mockConfigProperties);
    List<GoogleConfigurationProperties.ManagedAccount> result = source.getCredentialsDefinitions();

    // Assert
    assertThat(result).isEmpty();
  }

  @Test
  void testMultipleAccountSources() {
    // Arrange - Test with multiple account sources
    GoogleConfigurationProperties.ManagedAccount account1 =
        new GoogleConfigurationProperties.ManagedAccount();
    account1.setName("config-account");
    account1.setProject("config-project");

    when(mockConfigProperties.getAccounts()).thenReturn(Collections.singletonList(account1));

    GoogleAccountDefinitionSourceConfiguration sourceConfig =
        new GoogleAccountDefinitionSourceConfiguration();
    com.netflix.spinnaker.clouddriver.security.AccountDefinitionRepository mockRepository =
        mock(com.netflix.spinnaker.clouddriver.security.AccountDefinitionRepository.class);

    // Act
    CredentialsDefinitionSource<GoogleConfigurationProperties.ManagedAccount> source =
        sourceConfig.googleAccountSource(
            mockRepository, java.util.Optional.empty(), mockConfigProperties);
    List<GoogleConfigurationProperties.ManagedAccount> result = source.getCredentialsDefinitions();

    // Assert - should have account from config
    assertThat(result).hasSize(1);
    assertThat(result.get(0).getName()).isEqualTo("config-account");
  }
}
