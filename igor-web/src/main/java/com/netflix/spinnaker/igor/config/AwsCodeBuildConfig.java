/*
 * Copyright 2020 Amazon.com, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
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

package com.netflix.spinnaker.igor.config;

import com.amazonaws.auth.DefaultAWSCredentialsProviderChain;
import com.amazonaws.services.securitytoken.AWSSecurityTokenServiceClient;
import com.amazonaws.services.securitytoken.AWSSecurityTokenServiceClientBuilder;
import com.netflix.spinnaker.igor.codebuild.AwsCodeBuildAccount;
import com.netflix.spinnaker.igor.codebuild.AwsCodeBuildAccountRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnProperty("codebuild.enabled")
@EnableConfigurationProperties({AwsCodeBuildProperties.class})
public class AwsCodeBuildConfig {
  @Bean
  AwsCodeBuildAccountRepository awsCodeBuildAccountRepository(
      AwsCodeBuildProperties awsCodeBuildProperties) {
    AwsCodeBuildAccountRepository accounts = new AwsCodeBuildAccountRepository();
    awsCodeBuildProperties
        .getAccounts()
        .forEach(
            a -> {
              AwsCodeBuildAccount account =
                  new AwsCodeBuildAccount(a.getAccountId(), a.getAssumeRole(), a.getRegion());
              accounts.addAccount(a.getName(), account);
            });
    return accounts;
  }

  @Bean
  AWSSecurityTokenServiceClient awsSecurityTokenServiceClient() {
    return (AWSSecurityTokenServiceClient)
        AWSSecurityTokenServiceClientBuilder.standard()
            .withCredentials(DefaultAWSCredentialsProviderChain.getInstance())
            .build();
  }
}
