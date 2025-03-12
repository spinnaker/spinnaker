/*
 * Copyright 2021 Netflix, Inc.
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

package com.netflix.spinnaker.clouddriver.aws;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

import com.netflix.spinnaker.clouddriver.aws.security.NetflixAmazonCredentials;
import com.netflix.spinnaker.clouddriver.aws.security.config.AccountsConfiguration;
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
public class AwsTestConfiguration {

  @Value("${aws.primaryAccount}")
  private String AWS_ACCOUNT_NAME;

  @Bean
  @Primary
  public CompositeCredentialsRepository<AccountCredentials> compositeCredentialsRepository() {
    NetflixAmazonCredentials awsCreds = TestCredential.named(AWS_ACCOUNT_NAME);
    CompositeCredentialsRepository<AccountCredentials> repo =
        mock(CompositeCredentialsRepository.class);
    when(repo.getCredentials(eq(AWS_ACCOUNT_NAME), eq("aws"))).thenReturn(awsCreds);
    when(repo.getFirstCredentialsWithName(AWS_ACCOUNT_NAME)).thenReturn(awsCreds);
    return repo;
  }

  @Bean
  @Primary
  public CredentialsParser amazonCredentialsParser() {
    CredentialsParser parser = mock(CredentialsParser.class, withSettings().verboseLogging());
    when(parser.parse(any()))
        .thenAnswer(
            (Answer<NetflixAmazonCredentials>)
                invocation -> {
                  AccountsConfiguration.Account account =
                      invocation.getArgument(0, AccountsConfiguration.Account.class);
                  return TestCredential.named(account.getName());
                });
    return parser;
  }
}
