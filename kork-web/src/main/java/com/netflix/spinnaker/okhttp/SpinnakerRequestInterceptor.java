/*
 * Copyright 2019 Netflix, Inc.
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

package com.netflix.spinnaker.okhttp;

import com.netflix.spinnaker.security.AuthenticatedRequest;
import retrofit.RequestInterceptor;

public class SpinnakerRequestInterceptor implements RequestInterceptor {
  private final OkHttpClientConfigurationProperties okHttpClientConfigurationProperties;

  public SpinnakerRequestInterceptor(
      OkHttpClientConfigurationProperties okHttpClientConfigurationProperties) {
    this.okHttpClientConfigurationProperties = okHttpClientConfigurationProperties;
  }

  public void intercept(final RequestFacade request) {
    if (!okHttpClientConfigurationProperties.isPropagateSpinnakerHeaders()) {
      // noop
      return;
    }

    AuthenticatedRequest.getAuthenticationHeaders()
        .forEach((key, value) -> value.ifPresent(s -> request.addHeader(key, s)));
  }
}
