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

import com.amazonaws.ClientConfiguration;
import com.amazonaws.retry.PredefinedBackoffStrategies;
import com.amazonaws.retry.RetryPolicy;
import com.amazonaws.services.lambda.AWSLambda;
import com.netflix.spinnaker.clouddriver.aws.security.AmazonClientProvider;
import com.netflix.spinnaker.clouddriver.aws.security.NetflixAmazonCredentials;
import com.netflix.spinnaker.clouddriver.lambda.deploy.exception.InvalidAccountException;
import com.netflix.spinnaker.config.LambdaServiceConfig;
import org.springframework.beans.factory.annotation.Autowired;

public class LambdaClientProvider {
  @Autowired protected AmazonClientProvider amazonClientProvider;

  @Autowired protected LambdaServiceConfig operationsConfig;
  private String region;
  private NetflixAmazonCredentials credentials;

  public LambdaClientProvider(String region, NetflixAmazonCredentials credentials) {
    this.region = region;
    this.credentials = credentials;
  }

  // See the AwsSdkClientSupplier class.  IN particular the "load" method.  This is a MOSTLY cached
  // operation
  // COULD ALSO potentially have timeouts eventually PER region so API calls to a completely
  // different region would allow
  // different timeouts.  future thing with maybe a more intelligent selector pattern that allows
  // chaining of
  // timeouts based upon a precedent/business logic (e.g. if configured, and no account overrides
  // use region timeouts)
  protected AWSLambda getLambdaClient() {

    ClientConfiguration clientConfiguration = new ClientConfiguration();
    clientConfiguration.setSocketTimeout(operationsConfig.getInvokeTimeoutMs());
    // only override if non-negative, and can't just set to the negative default :(
    if (operationsConfig.getRetry().getRetries() >= 0) {
      clientConfiguration.setRetryPolicy(
          RetryPolicy.builder()
              .withBackoffStrategy(
                  new PredefinedBackoffStrategies.SDKDefaultBackoffStrategy(
                      100, 500, operationsConfig.getRetry().getTimeout() * 1000))
              .withMaxErrorRetry(operationsConfig.getRetry().getRetries())
              .build());
      // doing it here as well as in the retry policy to be safe.
      clientConfiguration.setMaxErrorRetry(operationsConfig.getRetry().getRetries());
    }

    if (!credentials.getLambdaEnabled()) {
      throw new InvalidAccountException("AWS Lambda is not enabled for provided account. \n");
    }
    // Note this is a CACHED response call.  AKA the clientConfig is ONLY set when this is first
    // cached/loaded
    // and won't be changed if OTHER requests make this.
    return amazonClientProvider.getAmazonLambda(credentials, clientConfiguration, region);
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
