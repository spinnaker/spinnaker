/*
 * Copyright 2020 Amazon.com, Inc. or its affiliates. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License").
 * You may not use this file except in compliance with the License.
 * A copy of the License is located at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR
 * CONDITIONS OF ANY KIND, either express or implied. See the License
 * for the specific language governing permissions and limitations under
 * the License.
 */

package com.netflix.spinnaker.clouddriver.ecs;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import com.netflix.spinnaker.clouddriver.aws.security.NetflixAmazonCredentials;
import com.netflix.spinnaker.clouddriver.aws.security.config.CredentialsConfig;
import com.netflix.spinnaker.clouddriver.ecs.security.NetflixAssumeRoleEcsCredentials;
import com.netflix.spinnaker.clouddriver.ecs.security.NetflixECSCredentials;
import com.netflix.spinnaker.clouddriver.security.AccountCredentials;
import com.netflix.spinnaker.credentials.CompositeCredentialsRepository;
import com.netflix.spinnaker.credentials.definition.CredentialsParser;
import org.mockito.stubbing.Answer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.TestPropertySource;

@TestConfiguration
@TestPropertySource(properties = {"spring.config.location = classpath:clouddriver.yml"})
public class EcsTestConfiguration {

  @Value("${ecs.primaryAccount}")
  protected String ECS_ACCOUNT_NAME;

  @Value("${ecs.accounts[1].name}")
  protected String ECS_MONIKER_ACCOUNT_NAME;

  @Value("${aws.primaryAccount}")
  protected String AWS_ACCOUNT_NAME;

  @Bean
  @Primary
  public CompositeCredentialsRepository<AccountCredentials> compositeCredentialsRepository() {
    NetflixAmazonCredentials awsCreds = TestCredential.named(AWS_ACCOUNT_NAME);
    NetflixECSCredentials ecsCreds =
        new NetflixAssumeRoleEcsCredentials(
            TestCredential.assumeRoleNamed(ECS_ACCOUNT_NAME), AWS_ACCOUNT_NAME);
    NetflixECSCredentials ecsMonikerCreds =
        new NetflixAssumeRoleEcsCredentials(
            TestCredential.assumeRoleNamed(ECS_MONIKER_ACCOUNT_NAME), AWS_ACCOUNT_NAME);
    CompositeCredentialsRepository<AccountCredentials> repo =
        mock(CompositeCredentialsRepository.class);
    when(repo.getCredentials(eq(AWS_ACCOUNT_NAME), eq("aws"))).thenReturn(awsCreds);
    when(repo.getCredentials(eq(ECS_ACCOUNT_NAME), eq("ecs"))).thenReturn(ecsCreds);
    when(repo.getCredentials(eq(ECS_MONIKER_ACCOUNT_NAME), eq("ecs"))).thenReturn(ecsMonikerCreds);
    when(repo.getFirstCredentialsWithName(AWS_ACCOUNT_NAME)).thenReturn(awsCreds);
    when(repo.getFirstCredentialsWithName(ECS_ACCOUNT_NAME)).thenReturn(ecsCreds);
    when(repo.getFirstCredentialsWithName(ECS_MONIKER_ACCOUNT_NAME)).thenReturn(ecsMonikerCreds);
    return repo;
  }

  @Bean("amazonCredentialsParser")
  @Primary
  public CredentialsParser amazonCredentialsParser() {
    CredentialsParser parser = mock(CredentialsParser.class, withSettings().verboseLogging());
    when(parser.parse(any()))
        .thenAnswer(
            (Answer<NetflixAmazonCredentials>)
                invocation -> {
                  CredentialsConfig.Account account =
                      invocation.getArgument(0, CredentialsConfig.Account.class);
                  return TestCredential.assumeRoleNamed(account.getName());
                });
    return parser;
  }
}
