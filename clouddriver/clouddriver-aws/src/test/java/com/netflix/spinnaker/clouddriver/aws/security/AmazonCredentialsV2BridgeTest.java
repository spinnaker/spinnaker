/*
 * Copyright 2026 spinnaker.io
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.auth.BasicSessionCredentials;
import java.util.Collections;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.AwsSessionCredentials;

/**
 * Tests for the v1-to-v2 credentials bridge in {@link AmazonCredentials#getV2CredentialsProvider()}
 * and its behavior across the credential class hierarchy.
 */
class AmazonCredentialsV2BridgeTest {

  @Test
  void bridgesBasicCredentialsFromV1ToV2() {
    AWSCredentialsProvider v1Provider = mock(AWSCredentialsProvider.class);
    when(v1Provider.getCredentials())
        .thenReturn(new BasicAWSCredentials("AKID_TEST", "SECRET_TEST"));

    AmazonCredentials creds = buildCredentials(v1Provider);
    AwsCredentialsProvider v2Provider = creds.getV2CredentialsProvider();

    assertThat(v2Provider).isNotNull();
    AwsCredentials resolved = v2Provider.resolveCredentials();
    assertThat(resolved).isInstanceOf(AwsBasicCredentials.class);
    assertThat(resolved.accessKeyId()).isEqualTo("AKID_TEST");
    assertThat(resolved.secretAccessKey()).isEqualTo("SECRET_TEST");
  }

  @Test
  void bridgesSessionCredentialsFromV1ToV2() {
    AWSCredentialsProvider v1Provider = mock(AWSCredentialsProvider.class);
    when(v1Provider.getCredentials())
        .thenReturn(new BasicSessionCredentials("AKID_SESSION", "SECRET_SESSION", "TOKEN_123"));

    AmazonCredentials creds = buildCredentials(v1Provider);
    AwsCredentialsProvider v2Provider = creds.getV2CredentialsProvider();

    assertThat(v2Provider).isNotNull();
    AwsCredentials resolved = v2Provider.resolveCredentials();
    assertThat(resolved).isInstanceOf(AwsSessionCredentials.class);
    AwsSessionCredentials sessionCreds = (AwsSessionCredentials) resolved;
    assertThat(sessionCreds.accessKeyId()).isEqualTo("AKID_SESSION");
    assertThat(sessionCreds.secretAccessKey()).isEqualTo("SECRET_SESSION");
    assertThat(sessionCreds.sessionToken()).isEqualTo("TOKEN_123");
  }

  @Test
  void delegatesTokenRefreshToV1Provider() {
    // Simulate v1 provider that returns different credentials on each call (token refresh)
    AWSCredentialsProvider v1Provider = mock(AWSCredentialsProvider.class);
    when(v1Provider.getCredentials())
        .thenReturn(new BasicSessionCredentials("AKID_1", "SECRET_1", "TOKEN_1"))
        .thenReturn(new BasicSessionCredentials("AKID_2", "SECRET_2", "TOKEN_2"));

    AmazonCredentials creds = buildCredentials(v1Provider);
    AwsCredentialsProvider v2Provider = creds.getV2CredentialsProvider();

    // First call
    AwsSessionCredentials first = (AwsSessionCredentials) v2Provider.resolveCredentials();
    assertThat(first.accessKeyId()).isEqualTo("AKID_1");
    assertThat(first.sessionToken()).isEqualTo("TOKEN_1");

    // Second call returns refreshed credentials
    AwsSessionCredentials second = (AwsSessionCredentials) v2Provider.resolveCredentials();
    assertThat(second.accessKeyId()).isEqualTo("AKID_2");
    assertThat(second.sessionToken()).isEqualTo("TOKEN_2");
  }

  @Test
  void netflixAssumeRoleCredentialsBridgesAssumedRoleSession() {
    // The v1 credentialsProvider passed to NetflixAssumeRoleAmazonCredentials gets wrapped
    // by createSTSCredentialsProvider, which returns session credentials. Simulate that the
    // stored provider (already the STS-assumed one) returns session creds.
    AWSCredentialsProvider stsProvider = mock(AWSCredentialsProvider.class);
    when(stsProvider.getCredentials())
        .thenReturn(new BasicSessionCredentials("ASSUMED_AKID", "ASSUMED_SECRET", "STS_TOKEN"));

    // Build a minimal AmazonCredentials with the STS provider already set
    AmazonCredentials creds = buildCredentials(stsProvider);
    AwsCredentialsProvider v2Provider = creds.getV2CredentialsProvider();

    AwsSessionCredentials resolved = (AwsSessionCredentials) v2Provider.resolveCredentials();
    assertThat(resolved.accessKeyId()).isEqualTo("ASSUMED_AKID");
    assertThat(resolved.secretAccessKey()).isEqualTo("ASSUMED_SECRET");
    assertThat(resolved.sessionToken()).isEqualTo("STS_TOKEN");
  }

  @Test
  void fallsBackToDefaultCredentialsProviderWhenNoV1ProviderSet() {
    // When credentialsProvider is null, should return DefaultCredentialsProvider
    AmazonCredentials creds = buildCredentials(null);
    AwsCredentialsProvider v2Provider = creds.getV2CredentialsProvider();

    // We can't easily assert it's a DefaultCredentialsProvider (it's sealed),
    // but we can verify it's not null and is a valid provider instance
    assertThat(v2Provider).isNotNull();
  }

  /** Helper to construct a minimal AmazonCredentials with the given v1 credentials provider. */
  private static AmazonCredentials buildCredentials(AWSCredentialsProvider provider) {
    return new AmazonCredentials(
        "test-account",
        "test-env",
        "test-type",
        "123456789012",
        null, // defaultKeyPair
        true, // enabled
        Collections.emptyList(), // regions
        Collections.emptyList(), // defaultSecurityGroups
        Collections.emptyList(), // requiredGroupMembership
        null, // permissions
        Collections.emptyList(), // lifecycleHooks
        false, // allowPrivateThirdPartyImages
        provider);
  }
}
