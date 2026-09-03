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

package com.netflix.spinnaker.clouddriver.aws.security.sdkclient;

import static org.assertj.core.api.Assertions.assertThat;

import com.amazonaws.ClientConfiguration;
import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.retry.PredefinedRetryPolicies;
import com.amazonaws.retry.RetryPolicy;
import com.amazonaws.services.lambda.AWSLambda;
import com.amazonaws.services.lambda.AWSLambdaClientBuilder;
import com.netflix.spectator.api.NoopRegistry;
import com.netflix.spectator.api.Registry;
import com.netflix.spinnaker.clouddriver.core.limits.ServiceLimitConfigurationBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link AwsSdkClientSupplier}, in particular the cache-key identity of {@code
 * ClientConfiguration} tuning (see {@code LambdaClientProvider}, which requests distinct clients
 * for regular vs. synchronous-invoke Lambda calls).
 */
class AwsSdkClientSupplierTest {

  private AwsSdkClientSupplier supplier;

  private static AWSCredentialsProvider dummyCreds() {
    return new AWSStaticCredentialsProvider(new BasicAWSCredentials("key", "secret"));
  }

  @BeforeEach
  void setUp() {
    Registry registry = new NoopRegistry();
    RateLimiterSupplier rateLimiterSupplier =
        new RateLimiterSupplier(new ServiceLimitConfigurationBuilder().build(), registry);
    RetryPolicy retryPolicy = PredefinedRetryPolicies.getDefaultRetryPolicy();
    supplier =
        new AwsSdkClientSupplier(rateLimiterSupplier, registry, retryPolicy, null, null, false);
  }

  @Test
  void differentSocketTimeoutReturnsDifferentInstance() {
    // A short-timeout (default) client and a long-timeout invoke client for the same account,
    // region, and service must be distinct cached instances.
    AWSCredentialsProvider creds = dummyCreds();
    ClientConfiguration shortSocket = new ClientConfiguration();
    shortSocket.setSocketTimeout(50_000);
    ClientConfiguration longSocket = new ClientConfiguration();
    longSocket.setSocketTimeout(15 * 60 * 1000);

    AWSLambda shortClient =
        supplier.getClient(
            AWSLambdaClientBuilder.class, AWSLambda.class, "acct", creds, "us-east-1", shortSocket);
    AWSLambda longClient =
        supplier.getClient(
            AWSLambdaClientBuilder.class, AWSLambda.class, "acct", creds, "us-east-1", longSocket);

    assertThat(shortClient).isNotSameAs(longClient);
  }

  @Test
  void equalClientConfigurationReturnsSameInstance() {
    // Config participates in cache identity by value: equal field values reuse the cached client.
    AWSCredentialsProvider creds = dummyCreds();

    ClientConfiguration first = new ClientConfiguration();
    first.setSocketTimeout(50_000);
    ClientConfiguration second = new ClientConfiguration();
    second.setSocketTimeout(50_000);

    AWSLambda firstClient =
        supplier.getClient(
            AWSLambdaClientBuilder.class, AWSLambda.class, "acct", creds, "us-east-1", first);
    AWSLambda secondClient =
        supplier.getClient(
            AWSLambdaClientBuilder.class, AWSLambda.class, "acct", creds, "us-east-1", second);

    assertThat(firstClient).isSameAs(secondClient);
  }
}
