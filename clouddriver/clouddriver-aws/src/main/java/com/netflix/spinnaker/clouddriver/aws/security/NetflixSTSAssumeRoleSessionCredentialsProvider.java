/*
 * Copyright 2016 Netflix, Inc.
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

package com.netflix.spinnaker.clouddriver.aws.security;

import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.auth.AWSSessionCredentials;
import com.amazonaws.auth.AWSSessionCredentialsProvider;
import com.amazonaws.auth.STSAssumeRoleSessionCredentialsProvider;
import com.amazonaws.client.builder.AwsClientBuilder.EndpointConfiguration;
import com.amazonaws.services.securitytoken.AWSSecurityTokenServiceClient;
import com.netflix.spinnaker.clouddriver.aws.security.sdkclient.SpinnakerAwsRegionProvider;
import java.io.Closeable;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class NetflixSTSAssumeRoleSessionCredentialsProvider
    implements AWSSessionCredentialsProvider, Closeable {

  private final String accountId;
  private final String roleArn;
  private final String region;
  private final Integer sessionDurationSeconds;
  private final STSAssumeRoleSessionCredentialsProvider delegate;

  public NetflixSTSAssumeRoleSessionCredentialsProvider(
      AWSCredentialsProvider longLivedCredentialsProvider,
      String roleArn,
      String roleSessionName,
      Integer sessionDurationSeconds,
      String accountId,
      String externalId) {

    this.accountId = accountId;
    this.roleArn = roleArn;
    this.sessionDurationSeconds = sessionDurationSeconds;

    var chain = new SpinnakerAwsRegionProvider();
    this.region = chain.getRegion();

    log.debug(
        "Setting up a credentials provider session named {} in the region {} for account {} using role {} "
            + "with {} duration",
        roleSessionName,
        this.region,
        accountId,
        roleArn,
        sessionDurationSeconds == null ? "default" : sessionDurationSeconds + "seconds");

    var stsClientBuilder =
        AWSSecurityTokenServiceClient.builder().withCredentials(longLivedCredentialsProvider);

    if (roleArn.contains("aws-us-gov")) {
      stsClientBuilder.withEndpointConfiguration(
          new EndpointConfiguration("sts.us-gov-west-1.amazonaws.com", this.region));
    } else if (roleArn.contains("aws-cn")) {
      stsClientBuilder.withEndpointConfiguration(
          new EndpointConfiguration("sts.cn-north-1.amazonaws.com.cn", this.region));
    } else {
      stsClientBuilder.withRegion(this.region);
    }

    STSAssumeRoleSessionCredentialsProvider.Builder stsSessionProviderBuilder =
        new STSAssumeRoleSessionCredentialsProvider.Builder(roleArn, roleSessionName)
            .withExternalId(externalId)
            .withStsClient(stsClientBuilder.build());

    if (sessionDurationSeconds != null) {
      stsSessionProviderBuilder.withRoleSessionDurationSeconds(sessionDurationSeconds);
    }

    delegate = stsSessionProviderBuilder.build();
  }

  public String getAccountId() {
    return accountId;
  }

  @Override
  public AWSSessionCredentials getCredentials() {
    log.debug(
        "Getting AWS Session credentials for account {} with role {} in region {}",
        accountId,
        roleArn,
        region);
    return delegate.getCredentials();
  }

  @Override
  public void refresh() {
    log.debug(
        "Refreshing AWS Session credentials for account {} with role {} in region {} with {} duration",
        accountId,
        roleArn,
        region,
        sessionDurationSeconds == null ? "default" : sessionDurationSeconds + "seconds");
    delegate.refresh();
  }

  @Override
  public void close() {
    delegate.close();
  }
}
