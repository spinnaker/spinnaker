/*
 * Copyright 2023 Armory, Inc.
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
 */

package com.netflix.spinnaker.clouddriver.lambda.deploy.ops;

import com.netflix.spinnaker.clouddriver.aws.security.AmazonClientProvider;
import com.netflix.spinnaker.clouddriver.aws.security.NetflixAmazonCredentials;
import com.netflix.spinnaker.clouddriver.aws.security.sdkclient.AwsSdkV2ClientConfiguration;
import com.netflix.spinnaker.clouddriver.lambda.deploy.exception.InvalidAccountException;
import com.netflix.spinnaker.config.LambdaServiceConfig;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Autowired;
import software.amazon.awssdk.services.lambda.LambdaClient;

public class LambdaClientProvider {
  @Autowired protected AmazonClientProvider amazonClientProvider;

  @Autowired protected LambdaServiceConfig operationsConfig;

  private String region;
  private NetflixAmazonCredentials credentials;

  public LambdaClientProvider(String region, NetflixAmazonCredentials credentials) {
    this.region = region;
    this.credentials = credentials;
  }

  /**
   * The maximum time an AWS Lambda function can run (15 minutes). Used as the socket-timeout
   * ceiling for the dedicated invoke client so that a synchronous {@code RequestResponse} invoke —
   * which holds the connection idle for the entire function execution — is never cut short below
   * this hard limit. The per-request {@code apiCallTimeout} set by {@code
   * InvokeLambdaAtomicOperation} remains the authoritative, tunable bound within this ceiling.
   */
  private static final Duration LAMBDA_MAX_EXECUTION = Duration.ofMinutes(15);

  protected LambdaClient getLambdaClient() {
    return buildLambdaClient(
        buildClientConfiguration(Duration.ofMillis(operationsConfig.getInvokeTimeoutMs())));
  }

  /**
   * Returns a Lambda client dedicated to synchronous invocations. It differs from {@link
   * #getLambdaClient()} only in its socket timeout, which is raised to the Lambda maximum so the
   * per-request {@code apiCallTimeout} governs how long an invoke may run rather than being capped
   * by the shorter default socket timeout used for all other (short) Lambda operations.
   *
   * <p>The distinct {@link AwsSdkV2ClientConfiguration} yields a separate cached client, leaving
   * the shared non-invoke client's timeouts unchanged.
   */
  protected LambdaClient getInvokeLambdaClient() {
    return buildLambdaClient(buildClientConfiguration(LAMBDA_MAX_EXECUTION));
  }

  private LambdaClient buildLambdaClient(AwsSdkV2ClientConfiguration clientConfiguration) {
    if (!credentials.isLambdaEnabled()) {
      throw new InvalidAccountException("AWS Lambda is not enabled for provided account. \n");
    }
    return amazonClientProvider.getLambdaV2(credentials, region, clientConfiguration);
  }

  /**
   * Translates the Lambda operation defaults into the v2 client tuning, applying the given socket
   * timeout. A negative retry count defers to the SDK's default retry policy (see {@link
   * LambdaServiceConfig}).
   */
  private AwsSdkV2ClientConfiguration buildClientConfiguration(Duration socketTimeout) {
    AwsSdkV2ClientConfiguration.AwsSdkV2ClientConfigurationBuilder builder =
        AwsSdkV2ClientConfiguration.builder()
            .socketTimeout(socketTimeout)
            .tcpKeepAlive(operationsConfig.isTcpKeepAlive());
    if (operationsConfig.getRetry().getRetries() >= 0) {
      builder.maxErrorRetry(operationsConfig.getRetry().getRetries());
    }
    return builder.build();
  }

  protected String getRegion() {
    return region;
  }

  protected NetflixAmazonCredentials getCredentials() {
    return credentials;
  }

  public AmazonClientProvider getAmazonClientProvider() {
    return amazonClientProvider;
  }
}
