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

package com.netflix.spinnaker.kork.web.interceptors

import com.netflix.spinnaker.kork.common.Header
import com.netflix.spinnaker.okhttp.OkHttpClientConfigurationProperties
import com.netflix.spinnaker.okhttp.SpinnakerRequestHeaderInterceptor
import com.netflix.spinnaker.security.AuthenticatedRequest
import okhttp3.Interceptor
import okhttp3.Request
import org.mockito.Mockito
import spock.lang.Specification

class SpinnakerRequestHeaderInterceptorTest extends Specification {

  def "request contains authorization header"() {
    Mockito.mockStatic(AuthenticatedRequest.class)
    Map<String, Optional<String>> authHeaders = new HashMap<String, Optional<String>>(){}
    authHeaders.put(Header.USER.getHeader(), Optional.of("some user"))
    authHeaders.put(Header.ACCOUNTS.getHeader(), Optional.of("Some ACCOUNTS"))
    def okHttpClientConfigurationProperties = new OkHttpClientConfigurationProperties();
    def headerInterceptor = new SpinnakerRequestHeaderInterceptor(okHttpClientConfigurationProperties)
    def chain = Mock(Interceptor.Chain) {
      request() >> new Request.Builder()
        .url("http://1.1.1.1/heath-check")
        .build()
    }

    Mockito.when(AuthenticatedRequest.authenticationHeaders)
      .thenReturn(authHeaders)

    when: "running the interceptor under test"
    headerInterceptor.intercept(chain)

    then: "the expected authorization header is added to the request before proceeding"
    1 * chain.proceed({ Request request -> request.headers(Header.USER.getHeader()) == ["some user"] &&
      request.headers(Header.ACCOUNTS.getHeader()) == ["Some ACCOUNTS"]})
  }
}
