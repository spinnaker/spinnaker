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

import com.netflix.spinnaker.igor.codebuild.AwsCodeBuildAccount;
import com.netflix.spinnaker.igor.codebuild.AwsCodeBuildAccountRepository;
import java.util.Objects;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sts.StsClient;
import software.amazon.awssdk.services.sts.auth.StsAssumeRoleCredentialsProvider;

@Configuration
@ConditionalOnProperty("codebuild.enabled")
@EnableConfigurationProperties({AwsCodeBuildProperties.class})
public class AwsCodeBuildConfig {
  @Bean("awsCodeBuildAccountRepository")
  AwsCodeBuildAccountRepository awsCodeBuildAccountRepository(
      AwsCodeBuildProperties awsCodeBuildProperties,
      AwsCredentialsProvider awsCredentialsProvider) {
    AwsCodeBuildAccountRepository accounts = new AwsCodeBuildAccountRepository();
    awsCodeBuildProperties
        .getAccounts()
        .forEach(
            a -> {
              AwsCredentialsProvider credentialsProvider = awsCredentialsProvider;
              if (a.getAccountId() != null && a.getAssumeRole() != null) {
                StsClient stsClient =
                    StsClient.builder()
                        .credentialsProvider(awsCredentialsProvider)
                        .region(Region.of(a.getRegion()))
                        .build();
                credentialsProvider =
                    StsAssumeRoleCredentialsProvider.builder()
                        .stsClient(stsClient)
                        .refreshRequest(
                            builder ->
                                builder
                                    .roleArn(getRoleArn(a.getAccountId(), a.getAssumeRole()))
                                    .roleSessionName("spinnaker-session"))
                        .build();
              }
              AwsCodeBuildAccount account =
                  new AwsCodeBuildAccount(credentialsProvider, a.getRegion());
              accounts.addAccount(a.getName(), account);
            });
    return accounts;
  }

  @Bean("awsCredentialsProvider")
  AwsCredentialsProvider awsCredentialsProvider(AwsCodeBuildProperties awsCodeBuildProperties) {
    if (awsCodeBuildProperties.getAccessKeyId() != null
        && !awsCodeBuildProperties.getAccessKeyId().isEmpty()
        && awsCodeBuildProperties.getSecretAccessKey() != null
        && !awsCodeBuildProperties.getSecretAccessKey().isEmpty()) {
      return StaticCredentialsProvider.create(
          AwsBasicCredentials.create(
              awsCodeBuildProperties.getAccessKeyId(),
              awsCodeBuildProperties.getSecretAccessKey()));
    }
    return DefaultCredentialsProvider.create();
  }

  private String getRoleArn(String accountId, String assumeRole) {
    String assumeRoleValue = Objects.requireNonNull(assumeRole, "assumeRole");
    if (!assumeRoleValue.startsWith("arn:")) {
      /**
       * GovCloud and China regions need to have the full arn passed because of differing formats
       * Govcloud: arn:aws-us-gov:iam China: arn:aws-cn:iam
       */
      assumeRoleValue =
          String.format(
              "arn:aws:iam::%s:%s",
              Objects.requireNonNull(accountId, "accountId"), assumeRoleValue);
    }
    return assumeRoleValue;
  }
}
