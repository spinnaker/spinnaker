/*
 * Copyright 2023 OpsMx, Inc.
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
import java.io.IOException;
import okhttp3.Interceptor;
import okhttp3.Request;
import okhttp3.Response;

/**
 * As {@link retrofit.RequestInterceptor} no longer present in retrofit2, we have to use {@link
 * okhttp3.Interceptor} to add authenticated headers to requests.
 */
public class SpinnakerRequestHeaderInterceptor implements Interceptor {

  private final OkHttpClientConfigurationProperties okHttpClientConfigurationProperties;

  public SpinnakerRequestHeaderInterceptor(
      OkHttpClientConfigurationProperties okHttpClientConfigurationProperties) {
    this.okHttpClientConfigurationProperties = okHttpClientConfigurationProperties;
  }

  @Override
  public Response intercept(Chain chain) throws IOException {
    Request.Builder builder = chain.request().newBuilder();
    if (!okHttpClientConfigurationProperties.getPropagateSpinnakerHeaders()) {
      return chain.proceed(builder.build());
    }

    AuthenticatedRequest.getAuthenticationHeaders()
        .forEach(
            (key, value) -> {
              if (value.isPresent()) {
                builder.addHeader(key, value.get());
              }
            });

    return chain.proceed(builder.build());
  }
}
