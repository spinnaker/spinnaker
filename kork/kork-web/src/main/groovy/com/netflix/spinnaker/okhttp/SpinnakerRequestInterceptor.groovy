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
import retrofit.RequestInterceptor

class SpinnakerRequestInterceptor implements RequestInterceptor {
  private final boolean propagateSpinnakerHeaders;

  /**
   * Don't propagate X-SPINNAKER-ACCOUNTS.  Only relevant when propagateSpinnakerHeaders is true.
   */
  private final boolean skipAccountsHeader;

  SpinnakerRequestInterceptor(boolean propagateSpinnakerHeaders) {
    this.propagateSpinnakerHeaders = propagateSpinnakerHeaders
    this.skipAccountsHeader = false
  }

  SpinnakerRequestInterceptor(boolean propagateSpinnakerHeaders,
                              boolean skipAccountsHeader) {
    this.propagateSpinnakerHeaders = propagateSpinnakerHeaders
    this.skipAccountsHeader = skipAccountsHeader
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
  }
}
