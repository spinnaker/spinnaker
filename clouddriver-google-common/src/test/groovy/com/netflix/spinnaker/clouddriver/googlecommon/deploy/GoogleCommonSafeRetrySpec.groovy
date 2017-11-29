/*
 * Copyright 2017 Google, Inc.
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

package com.netflix.spinnaker.clouddriver.googlecommon.deploy

import com.google.api.client.googleapis.json.GoogleJsonResponseException
import com.google.api.client.http.HttpHeaders
import com.google.api.client.http.HttpResponseException
import spock.lang.Specification
import spock.lang.Unroll

class GoogleCommonSafeRetrySpec extends Specification {

  @Unroll
  def "should retry on certain error codes"() {
    setup:
    HttpResponseException.Builder b = new HttpResponseException.Builder((int) code, null, new HttpHeaders())
    GoogleJsonResponseException e = new GoogleJsonResponseException(b, null)

    expect:
    retryable == GoogleCommonSafeRetry.isRetryable(e, [400])

    // Ensure non-GCP exceptions also cause retries.
    GoogleCommonSafeRetry.isRetryable(new SocketException(), [])

    where:
    code || retryable
    399  || false
    400  || true
    401  || false
    500  || true
    503  || true
  }
}
