/*
 * Copyright 2021 Armory, Inc.
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

package com.netflix.spinnaker.config;

import com.netflix.spinnaker.clouddriver.aws.security.AmazonClientProvider;
import lombok.Data;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * The "defaults" here apply to ALL AWS Lambda operations. Several settings on AWS operations have
 * interesting precedents. Examples below:
 *
 * <p>IF timeout is set on the pipeline that is BELOW the defaults that timeout will win, UNLESS
 * it's greater than the global timeout. Whoever is the MOST restrictive wins on timeouts.
 *
 * <p>Retry policy is different. A retry here of -1 is the default, which delegates to the RETRY
 * policy which tries to make intelligence decisions on whether it SHOULD retry (500's or timeouts
 * are generally retry situations) IF instead of trying to deterministically retry, you can set this
 * to a set value which will then determine how many retry attempts to make WITH the same policy
 * restrictions. See the {@link AmazonClientProvider.Builder#buildPolicy()} build policy method for
 * the default policy.
 *
 * <p>{@link
 * com.netflix.spinnaker.clouddriver.lambda.deploy.description.InvokeLambdaFunctionDescription}
 * operation. These are JUST defaults and nominally set higher so you can lower them PER request.
 */
@Component
@ConfigurationProperties(prefix = "aws.lambda")
@Data
public class LambdaServiceConfig {

  // Matches AWS SDK default value & moves the LambdaOperationsConfig stuff here
  // Supported new "ops" concept for timeouts that... really is dead on arrival due to how the SDK
  // and lambda code worked.
  @Value("#{'${aws.lambda.invokeTimeoutMs:${aws.lambda.ops.invokeTimeoutMs:50000}}'}")
  private int invokeTimeoutMs = 50000;

  private Retry retry = new Retry();

  /**
   * Duplicated by the {@link
   * com.netflix.spinnaker.clouddriver.aws.AwsConfigurationProperties.ClientConfig} class and the
   * native SDK Retry handling.
   */
  @Data
  public static class Retry {
    // SDK Default is 20 seconds.... this is a touch lower
    private int timeout = 15;
    // Default to the aws client max error retries if NOT set
    @Value("#{'${aws.lambda.retries:${aws.client.maxErrorRetry}}'}")
    private int retries = 3;
  }
}
