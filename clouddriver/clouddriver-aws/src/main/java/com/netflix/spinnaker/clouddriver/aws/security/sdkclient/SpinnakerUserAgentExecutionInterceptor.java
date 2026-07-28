/*
 * Copyright 2026 spinnaker.io
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

package com.netflix.spinnaker.clouddriver.aws.security.sdkclient;

import com.netflix.spinnaker.security.AuthenticatedRequest;
import java.util.List;
import software.amazon.awssdk.core.interceptor.Context;
import software.amazon.awssdk.core.interceptor.ExecutionAttributes;
import software.amazon.awssdk.core.interceptor.ExecutionInterceptor;
import software.amazon.awssdk.http.SdkHttpRequest;

/**
 * An AWS SDK v2 {@link ExecutionInterceptor} that appends the current Spinnaker user and execution
 * ID to the User-Agent header on each outgoing HTTP request. This is the v2 equivalent of {@link
 * com.netflix.spinnaker.clouddriver.aws.security.AddSpinnakerUserToUserAgentRequestHandler}.
 *
 * <p>The appended value follows the pattern: {@code spinnaker-user/<user>
 * spinnaker-executionId/<id>}
 */
public class SpinnakerUserAgentExecutionInterceptor implements ExecutionInterceptor {

  @Override
  public SdkHttpRequest modifyHttpRequest(
      Context.ModifyHttpRequest context, ExecutionAttributes executionAttributes) {
    String user = AuthenticatedRequest.getSpinnakerUser().orElse("unknown");
    String executionId = AuthenticatedRequest.getSpinnakerExecutionId().orElse("unknown");
    String suffix = String.format("spinnaker-user/%s spinnaker-executionId/%s", user, executionId);

    SdkHttpRequest httpRequest = context.httpRequest();
    List<String> existingUserAgent = httpRequest.headers().get("User-Agent");
    String currentUserAgent =
        (existingUserAgent != null && !existingUserAgent.isEmpty()) ? existingUserAgent.get(0) : "";
    String newUserAgent = currentUserAgent.isEmpty() ? suffix : currentUserAgent + " " + suffix;

    return httpRequest.toBuilder().putHeader("User-Agent", newUserAgent).build();
  }
}
