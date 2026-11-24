/*
 * Copyright 2026 Harness, Inc.
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

import static org.mockito.Mockito.*;

import com.netflix.spinnaker.clouddriver.aws.AmazonCloudProvider;
import com.netflix.spinnaker.clouddriver.aws.security.NetflixAmazonCredentials;
import com.netflix.spinnaker.clouddriver.ecs.TestCredential;
import com.netflix.spinnaker.clouddriver.security.AccountCredentials;
import com.netflix.spinnaker.credentials.CompositeCredentialsRepository;
import com.netflix.spinnaker.credentials.CredentialsRepository;
import com.netflix.spinnaker.credentials.definition.CredentialsDefinitionSource;
import com.netflix.spinnaker.credentials.definition.CredentialsParser;
import java.util.Collections;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class EcsCredentialsLoaderTest {

  private final ECSCredentialsConfig.Account account = new ECSCredentialsConfig.Account();

  private CredentialsParser<ECSCredentialsConfig.Account, NetflixECSCredentials> parser;
  private CredentialsRepository<NetflixECSCredentials> repository;
  private CompositeCredentialsRepository<AccountCredentials> compositeCredentialsRepository;
  private NetflixECSCredentials ecsCredentials;
  private EcsCredentialsLoader loader;

  @BeforeEach
  public void setUp() {
    account.setName("one-ecs");
    account.setAwsAccount("one");

    CredentialsDefinitionSource<ECSCredentialsConfig.Account> definitionSource =
        mock(CredentialsDefinitionSource.class);
    when(definitionSource.getCredentialsDefinitions())
        .thenReturn(Collections.singletonList(account));

    parser = mock(CredentialsParser.class);
    repository = mock(CredentialsRepository.class);
    when(repository.getAll()).thenReturn(Collections.emptySet());

    compositeCredentialsRepository = mock(CompositeCredentialsRepository.class);
    ecsCredentials = mock(NetflixECSCredentials.class);

    loader =
        new EcsCredentialsLoader(
            definitionSource, parser, repository, compositeCredentialsRepository);
  }

  @Test
  public void reParsesEcsAccountWhenBackingAwsAccountCredentialsChangeButNotOtherwise() {
    NetflixAmazonCredentials awsCredsV1 = TestCredential.named("one");
    // A distinct instance, e.g. after a new region is added to the AWS account.
    NetflixAmazonCredentials awsCredsV2 = TestCredential.named("one");
    when(compositeCredentialsRepository.getCredentials("one", AmazonCloudProvider.ID))
        .thenReturn(awsCredsV1, awsCredsV1, awsCredsV2);
    when(parser.parse(account)).thenReturn(ecsCredentials);

    // Loaded for the first time: the ECS account is parsed and saved.
    loader.load();
    verify(parser, times(1)).parse(account);
    verify(repository, times(1)).save(ecsCredentials);

    // Loaded again while the backing AWS account is unchanged: the ECS account definition
    // hasn't changed, so it isn't re-parsed.
    clearInvocations(parser);
    loader.load();
    verify(parser, never()).parse(any());

    // Loaded again after the backing AWS account changes (new credentials instance): the ECS
    // account is re-parsed even though its own definition is unchanged.
    clearInvocations(parser);
    loader.load();
    verify(parser, times(1)).parse(account);
  }
}
