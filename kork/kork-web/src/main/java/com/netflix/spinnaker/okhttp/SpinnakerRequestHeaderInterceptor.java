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

import com.netflix.spinnaker.kork.common.Header;
import com.netflix.spinnaker.security.AuthenticatedRequest;
import io.micrometer.core.instrument.util.StringUtils;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import okhttp3.Interceptor;
import okhttp3.Request;
import okhttp3.Response;
import org.slf4j.MDC;

/**
 * As {@link retrofit.RequestInterceptor} no longer present in retrofit2, we have to use {@link
 * okhttp3.Interceptor} to add authenticated headers to requests.
 */
@Slf4j
public class SpinnakerRequestHeaderInterceptor implements Interceptor {

  /**
   * Whether to propagate headers to outgoing HTTP requests. If false, no headers are propagated.
   * Not X-SPINNAKER-*, not additionalHeaders.
   */
  private final boolean propagateSpinnakerHeaders;

  /** Don't propagate X-SPINNAKER-ACCOUNTS. Only relevant when propagateSpinnakerHeaders is true. */
  private final boolean skipAccountsHeader;

  /**
   * Headers to propagate from the MDC, in addition to the X-SPINNAKER-* headers. Each element whose
   * the value in the MDC is non-empty is included in outgoing HTTP requests.
   */
  private final List<String> additionalHeaders;

  public SpinnakerRequestHeaderInterceptor(
      boolean propagateSpinnakerHeaders, List<String> additionalHeaders) {
    this(propagateSpinnakerHeaders, false /* skipAccountsHeader */, additionalHeaders);
  }

  public SpinnakerRequestHeaderInterceptor(
      boolean propagateSpinnakerHeaders,
      boolean skipAccountsHeader,
      List<String> additionalHeaders) {

    this.propagateSpinnakerHeaders = propagateSpinnakerHeaders;
    this.skipAccountsHeader = skipAccountsHeader;
    this.additionalHeaders = Optional.ofNullable(additionalHeaders).orElse(Collections.emptyList());
  }

  @Override
  public Response intercept(Chain chain) throws IOException {
    Request.Builder builder = chain.request().newBuilder();
    if (!propagateSpinnakerHeaders) {
      return chain.proceed(builder.build());
    }

    AuthenticatedRequest.getAuthenticationHeaders()
        .forEach(
            (key, value) -> {
              if (value.isPresent()
                  && (!skipAccountsHeader || !Header.ACCOUNTS.getHeader().equals(key))) {
                builder.addHeader(key, value.get());
              }
            });

    additionalHeaders.forEach(
        additionalHeader -> {
          String value = MDC.get(additionalHeader);
          if (StringUtils.isNotBlank(value)) {
            log.debug("adding {}={} to request", additionalHeader, value);
            builder.addHeader(additionalHeader, value);
          }
        });

    return chain.proceed(builder.build());
  }
}
