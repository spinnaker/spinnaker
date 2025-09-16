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

package com.netflix.spinnaker.clouddriver.ecs.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.netflix.spinnaker.clouddriver.aws.security.NetflixAmazonCredentials;
import com.netflix.spinnaker.clouddriver.aws.security.config.AccountsConfiguration;
import com.netflix.spinnaker.clouddriver.ecs.EcsCloudProvider;
import com.netflix.spinnaker.clouddriver.names.NamerRegistry;
import com.netflix.spinnaker.clouddriver.security.AccountCredentials;
import com.netflix.spinnaker.clouddriver.security.CredentialsInitializerSynchronizable;
import com.netflix.spinnaker.credentials.CompositeCredentialsRepository;
import com.netflix.spinnaker.credentials.CredentialsLifecycleHandler;
import com.netflix.spinnaker.credentials.CredentialsRepository;
import com.netflix.spinnaker.credentials.MapBackedCredentialsRepository;
import com.netflix.spinnaker.credentials.definition.AbstractCredentialsLoader;
import com.netflix.spinnaker.credentials.definition.BasicCredentialsLoader;
import com.netflix.spinnaker.credentials.definition.CredentialsDefinitionSource;
import com.netflix.spinnaker.credentials.definition.CredentialsParser;


import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class EcsCredentialsInitializerTest {

  private EcsCredentialsInitializer initializer;

  @BeforeEach
  public void setUp() {
    initializer = new EcsCredentialsInitializer();
  }

  @Test
  public void testEcsCredentialsConfigBean() {
    ECSCredentialsConfig config = initializer.ecsCredentialsConfig();
    assertThat(config).isNotNull();
    assertThat(config).isInstanceOf(ECSCredentialsConfig.class);
  }

  @Test
  public void testEcsCredentialsRepositoryBean() {
    CredentialsLifecycleHandler<NetflixECSCredentials> eventHandler = mock(CredentialsLifecycleHandler.class);

    CredentialsRepository<NetflixECSCredentials> repository = initializer.ecsCredentialsRepository(eventHandler);

    assertThat(repository).isNotNull();
    assertThat(repository).isInstanceOf(MapBackedCredentialsRepository.class);
    assertThat(repository.getType()).isEqualTo(EcsCloudProvider.ID);
  }

  @Test
  public void testEcsCredentialsParserBean() {
    ECSCredentialsConfig ecsCredentialsConfig = mock(ECSCredentialsConfig.class);
    CompositeCredentialsRepository<AccountCredentials> compositeCredentialsRepository = mock(CompositeCredentialsRepository.class);
    CredentialsParser<AccountsConfiguration.Account, NetflixAmazonCredentials> amazonCredentialsParser = mock(CredentialsParser.class);
    NamerRegistry namerRegistry = mock(NamerRegistry.class);

    CredentialsParser<ECSCredentialsConfig.Account, NetflixECSCredentials> parser = initializer.ecsCredentialsParser(
        ecsCredentialsConfig,
        compositeCredentialsRepository,
        amazonCredentialsParser,
        namerRegistry
    );

    assertThat(parser).isNotNull();
    assertThat(parser).isInstanceOf(EcsCredentialsParser.class);
  }

  @Test
  public void testEcsCredentialsLoaderBeanWithProvidedSource() {
    CredentialsParser<ECSCredentialsConfig.Account, NetflixECSCredentials> credentialsParser = mock(CredentialsParser.class);
    CredentialsRepository<NetflixECSCredentials> repository = mock(CredentialsRepository.class);
    ECSCredentialsConfig ecsCredentialsConfig = mock(ECSCredentialsConfig.class);
    CredentialsDefinitionSource<ECSCredentialsConfig.Account> ecsCredentialsSource = mock(CredentialsDefinitionSource.class);

    AbstractCredentialsLoader<NetflixECSCredentials> loader = initializer.ecsCredentialsLoader(
        credentialsParser,
        repository,
        ecsCredentialsConfig,
        ecsCredentialsSource
    );

    assertThat(loader).isNotNull();
    assertThat(loader).isInstanceOf(BasicCredentialsLoader.class);
  }

  @Test
  public void testEcsCredentialsInializerSynchronizable() {
    AbstractCredentialsLoader<NetflixECSCredentials> credentialsLoader = mock(AbstractCredentialsLoader.class);

    CredentialsInitializerSynchronizable synchronizable = initializer.ecsCredentialsInializerSynchronizable(credentialsLoader);

    assertThat(synchronizable).isNotNull();
    assertThat(synchronizable).isInstanceOf(CredentialsInitializerSynchronizable.class);

    synchronizable.synchronize();

    verify(credentialsLoader).load();
  }
}
