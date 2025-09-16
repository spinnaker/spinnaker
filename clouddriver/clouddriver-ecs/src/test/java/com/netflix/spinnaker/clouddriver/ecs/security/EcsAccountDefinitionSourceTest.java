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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;

import com.netflix.spinnaker.clouddriver.security.AccountDefinitionRepository;
import com.netflix.spinnaker.clouddriver.security.AccountDefinitionSource;
import com.netflix.spinnaker.credentials.definition.CredentialsDefinitionSource;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class EcsAccountDefinitionSourceTest {

  private EcsAccountDefinitionSource accountDefinitionSource;

  @BeforeEach
  public void setUp() {
    accountDefinitionSource = new EcsAccountDefinitionSource();
  }

  @Test
  public void testEcsAccountSourceReturnsAccountDefinitions() {

    ECSCredentialsConfig.Account mockAccount = new ECSCredentialsConfig.Account();
    mockAccount.setName("test-account");

    AccountDefinitionRepository repository = mock(AccountDefinitionRepository.class);

    doReturn(Collections.singletonList(mockAccount))
        .when(repository).listByType(eq("ecs"));

    ECSCredentialsConfig ecsCredentialsConfig = mock(ECSCredentialsConfig.class);
    Optional<List<CredentialsDefinitionSource<ECSCredentialsConfig.Account>>> emptyAdditionalSources =
        Optional.empty();

    CredentialsDefinitionSource<ECSCredentialsConfig.Account> source =
        accountDefinitionSource.ecsAccountSource(repository, emptyAdditionalSources, ecsCredentialsConfig);

    assertThat(source).isNotNull();
    assertThat(source).isInstanceOf(AccountDefinitionSource.class);

    List<ECSCredentialsConfig.Account> retrievedAccounts = source.getCredentialsDefinitions();

    assertThat(retrievedAccounts).isNotEmpty();
    assertThat(retrievedAccounts.get(0).getName()).isEqualTo("test-account");
  }
}
