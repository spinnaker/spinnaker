/*
 * Copyright 2016 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
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

package com.netflix.spinnaker.okhttp

import com.netflix.spinnaker.kork.common.Header
import com.netflix.spinnaker.security.AuthenticatedRequest
import groovy.util.logging.Slf4j
import io.micrometer.core.instrument.util.StringUtils
import org.slf4j.MDC
import retrofit.RequestInterceptor

/**
 * Propagate X-SPINNAKER-* headers, and additionally specified ones, from the
 * MDC into outgoing HTTP requests.
 */
@Slf4j
class SpinnakerRequestInterceptor implements RequestInterceptor {

  /**
   * Whether to propagate headers to outgoing HTTP requests.  If false, no
   * headers are propagated.  Not X-SPINNAKER-*, not additionalHeaders.
   */
  private final boolean propagateSpinnakerHeaders;

  /**
   * Don't propagate X-SPINNAKER-ACCOUNTS.  Only relevant when propagateSpinnakerHeaders is true.
   */
  private final boolean skipAccountsHeader;

  /**
   * Headers to propagate from the MDC, in addition to the X-SPINNAKER-*
   * headers.  Each element whose the value in the MDC is non-empty is included
   * in outgoing HTTP requests.
   */
  private final List<String> additionalHeaders;

  SpinnakerRequestInterceptor(boolean propagateSpinnakerHeaders,
                              List<String> additionalHeaders) {
    this(propagateSpinnakerHeaders, false /* skipAccountsHeader */, additionalHeaders)
  }

  SpinnakerRequestInterceptor(boolean propagateSpinnakerHeaders,
                              boolean skipAccountsHeader,
                              List<String> additionalHeaders) {
    this.propagateSpinnakerHeaders = propagateSpinnakerHeaders
    this.skipAccountsHeader = skipAccountsHeader
    this.additionalHeaders = Optional.ofNullable(additionalHeaders).orElse(Collections.emptyList())
  }

  void intercept(RequestInterceptor.RequestFacade request) {
    if (!propagateSpinnakerHeaders) {
      // noop
      return
    }

    AuthenticatedRequest.authenticationHeaders.each { String key, Optional<String> value ->
      if (value.present && (!skipAccountsHeader || !Header.ACCOUNTS.getHeader().equals(key))) {
        request.addHeader(key, value.get())
      }
    }

    additionalHeaders.forEach(additionalHeader -> {
      String value = MDC.get(additionalHeader);
      if (StringUtils.isNotBlank(value)) {
        log.debug("adding {}={} to request", additionalHeader, value);
        request.addHeader(additionalHeader, value);
      }
    });
  }
}
