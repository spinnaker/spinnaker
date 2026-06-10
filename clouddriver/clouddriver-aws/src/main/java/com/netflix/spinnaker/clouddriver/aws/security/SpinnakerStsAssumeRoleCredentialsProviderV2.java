/*
 * Copyright 2025 Netflix, Inc.
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

import java.time.Duration;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;
import software.amazon.awssdk.auth.credentials.AwsCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sts.StsClient;
import software.amazon.awssdk.services.sts.StsClientBuilder;
import software.amazon.awssdk.services.sts.auth.StsAssumeRoleCredentialsProvider;
import software.amazon.awssdk.services.sts.model.AssumeRoleRequest;

/**
 * AWS SDK v2 equivalent of {@link NetflixSTSAssumeRoleSessionCredentialsProvider}. Assumes the
 * given IAM role using the supplied long-lived {@link AwsCredentialsProvider} and returns
 * short-lived session credentials.
 *
 * <p>This provider is used by {@link AmazonCredentials#getV2CredentialsProvider()} for accounts
 * that have an {@code assumeRole} configured.
 */
@Slf4j
public class SpinnakerStsAssumeRoleCredentialsProviderV2 implements AwsCredentialsProvider {

  private final String accountId;
  private final String roleArn;
  private final StsAssumeRoleCredentialsProvider delegate;

  /**
   * @param longLivedCredentialsProvider base v2 credentials used to call STS
   * @param accountId AWS account ID (used for logging/diagnostics)
   * @param assumeRole role name or full ARN (e.g. {@code role/SpinnakerManaged} or {@code
   *     arn:aws:iam::123:role/SpinnakerManaged})
   * @param roleSessionName STS session name (defaults to {@code Spinnaker})
   * @param sessionDurationSeconds optional override for the role session duration; null uses the
   *     STS default (1 h)
   * @param externalId optional external ID required by the role's trust policy
   */
  public SpinnakerStsAssumeRoleCredentialsProviderV2(
      AwsCredentialsProvider longLivedCredentialsProvider,
      String accountId,
      String assumeRole,
      String roleSessionName,
      Integer sessionDurationSeconds,
      String externalId) {
    this.accountId = Objects.requireNonNull(accountId, "accountId");

    String resolvedArn = resolveRoleArn(assumeRole, accountId);
    this.roleArn = resolvedArn;

    log.debug(
        "Setting up v2 STS credentials provider for account {} using role {} with session name {}",
        accountId,
        resolvedArn,
        roleSessionName);

    AssumeRoleRequest.Builder requestBuilder =
        AssumeRoleRequest.builder().roleArn(resolvedArn).roleSessionName(roleSessionName);

    if (externalId != null && !externalId.isEmpty()) {
      requestBuilder.externalId(externalId);
    }
    if (sessionDurationSeconds != null) {
      requestBuilder.durationSeconds(sessionDurationSeconds);
    }
    AssumeRoleRequest request = requestBuilder.build();

    StsClientBuilder stsClientBuilder =
        StsClient.builder().credentialsProvider(longLivedCredentialsProvider);

    // Choose the STS endpoint for GovCloud / China partitions; standard AWS uses the global
    // endpoint which the default v2 region resolution handles automatically.
    if (resolvedArn.contains("aws-us-gov")) {
      stsClientBuilder.region(Region.US_GOV_WEST_1);
    } else if (resolvedArn.contains("aws-cn")) {
      stsClientBuilder.region(Region.CN_NORTH_1);
    }

    StsClient stsClient = stsClientBuilder.build();

    StsAssumeRoleCredentialsProvider.Builder providerBuilder =
        StsAssumeRoleCredentialsProvider.builder().stsClient(stsClient).refreshRequest(request);

    if (sessionDurationSeconds != null) {
      // Refresh ahead of the actual expiry to minimise clock-skew / renewal gaps.
      Duration refreshPeriod = Duration.ofSeconds(Math.max(60, sessionDurationSeconds - 60));
      providerBuilder.staleTime(Duration.ofSeconds(30)).prefetchTime(refreshPeriod);
    }

    this.delegate = providerBuilder.build();
  }

  /** Returns the AWS account ID this provider targets. */
  public String getAccountId() {
    return accountId;
  }

  /** Returns the fully-qualified role ARN being assumed. */
  public String getRoleArn() {
    return roleArn;
  }

  @Override
  public AwsCredentials resolveCredentials() {
    log.debug("Resolving v2 STS credentials for account {} with role {}", accountId, roleArn);
    return delegate.resolveCredentials();
  }

  // -------------------------------------------------------------------------
  // Helpers
  // -------------------------------------------------------------------------

  /**
   * Ensures the role is expressed as a full ARN. GovCloud and China need to use their respective
   * partition prefixes and must be passed as full ARNs by callers; standard AWS roles may be
   * provided as short names (e.g. {@code role/SpinnakerManaged}) which are expanded here.
   */
  static String resolveRoleArn(String assumeRole, String accountId) {
    Objects.requireNonNull(assumeRole, "assumeRole");
    if (assumeRole.startsWith("arn:")) {
      return assumeRole;
    }
    return String.format(
        "arn:aws:iam::%s:%s", Objects.requireNonNull(accountId, "accountId"), assumeRole);
  }
}
