/*
 * Copyright 2025 OpsMx, Inc.
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

package com.netflix.spinnaker.igor.codebuild;

import com.netflix.spinnaker.igor.exceptions.BuildJobError;
import com.netflix.spinnaker.security.AuthenticatedRequest;
import lombok.extern.slf4j.Slf4j;
import software.amazon.awssdk.awscore.AwsRequest;
import software.amazon.awssdk.awscore.AwsRequestOverrideConfiguration;
import software.amazon.awssdk.awscore.exception.AwsServiceException;
import software.amazon.awssdk.core.interceptor.Context;
import software.amazon.awssdk.core.interceptor.ExecutionAttributes;
import software.amazon.awssdk.core.interceptor.ExecutionInterceptor;

@Slf4j
public class AwsCodeBuildRequestInterceptor implements ExecutionInterceptor {

  @Override
  public AwsRequest modifyRequest(
      Context.ModifyRequest context, ExecutionAttributes executionAttributes) {
    String userAgent =
        String.format(
            "spinnaker-user/%s spinnaker-executionId/%s",
            AuthenticatedRequest.getSpinnakerUser().orElse("unknown"),
            AuthenticatedRequest.getSpinnakerExecutionId().orElse("unknown"));
    AwsRequest awsRequest = (AwsRequest) context.request();

    AwsRequestOverrideConfiguration overrideConfig =
        awsRequest
            .overrideConfiguration()
            .map(
                config -> config.toBuilder().addApiName(builder -> builder.name(userAgent)).build())
            .orElse(
                AwsRequestOverrideConfiguration.builder()
                    .addApiName(builder -> builder.name(userAgent))
                    .build());

    return awsRequest.toBuilder().overrideConfiguration(overrideConfig).build();
  }

  @Override
  public void onExecutionFailure(
      Context.FailedExecution context, ExecutionAttributes executionAttributes) {
    Throwable exception = context.exception();
    if (exception instanceof AwsServiceException awsException) {
      int statusCode = awsException.statusCode();
      if (statusCode >= 400 && statusCode < 500) {
        log.warn(awsException.getMessage());
        throw new BuildJobError(awsException.getMessage());
      } else {
        log.error(awsException.getMessage());
        throw new RuntimeException(awsException);
      }
    } else {
      log.error(exception.getMessage());
      throw new RuntimeException(exception);
    }
  }
}
