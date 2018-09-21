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
import com.google.api.client.googleapis.testing.json.GoogleJsonResponseExceptionFactoryTesting
import com.google.api.client.http.HttpHeaders
import com.google.api.client.http.HttpResponseException
import com.google.api.client.testing.json.MockJsonFactory
import com.google.common.collect.ImmutableMap;
import com.netflix.spectator.api.DefaultRegistry;
import com.netflix.spectator.api.Registry;
import com.netflix.spinnaker.clouddriver.data.task.Task
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Unroll

class GoogleCommonSafeRetrySpec extends Specification {

  @Shared
  Registry registry = new DefaultRegistry()

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

  GoogleCommonSafeRetry makeRetrier() {
    return new GoogleCommonSafeRetry() {
        Exception providerOperationException(String message) {
          throw new IllegalStateException(message);
        }
    }
  }

  def "no_retry"() {
    given:
      Closure  mockClosure = Mock(Closure)
      GoogleCommonSafeRetry retrier = makeRetrier()
      int maxRetries = 10

    when:
      Object result = retrier.doRetry(
            mockClosure, "resource", Mock(Task),
            Arrays.asList(500), Arrays.asList(404),
            new Long(-1), new Long(-1), new Long(-1), new Long(maxRetries),
            ImmutableMap.of("action", "test"), registry)
    then:
      1 * mockClosure() >> "Hello World"
      result == "Hello World"
  }

  def "retry_until_success"() {
    given:
      Closure  mockClosure = Mock(Closure)
      GoogleCommonSafeRetry retrier = makeRetrier()
      int maxRetries = 10

    when:
      Object result = retrier.doRetry(
            mockClosure, "resource", Mock(Task),
            Arrays.asList(500), Arrays.asList(404),
            new Long(-1), new Long(-1), new Long(-1), new Long(maxRetries),
            ImmutableMap.of("action", "test"), registry)
    then:
      2 * mockClosure() >> {
       throw new SocketTimeoutException()
      }
      2 * mockClosure() >> {
       throw GoogleJsonResponseExceptionFactoryTesting.newMock(
            new MockJsonFactory(), 500, "oops")
      }
      1 * mockClosure() >> "Hello World"
      result == "Hello World"
  }

  def "retry_until_exhausted"() {
    given:
      Closure  mockClosure = Mock(Closure)
      GoogleCommonSafeRetry retrier = makeRetrier()
      int maxAttempts = 4

    when:
      Object result = retrier.doRetry(
            mockClosure, "resource", Mock(Task),
            Arrays.asList(500), Arrays.asList(404),
            new Long(-1), new Long(-1), new Long(-1), new Long(maxAttempts),
            ImmutableMap.of("action", "test"), registry)
    then:
      2 * mockClosure() >> {
       throw GoogleJsonResponseExceptionFactoryTesting.newMock(
            new MockJsonFactory(), 500, "oops")
      }
      2 * mockClosure() >> {
       throw new SocketTimeoutException()
      }
      thrown(IllegalStateException)
  }

  def "retry_until_404_ok"() {
    given:
      Closure  mockClosure = Mock(Closure)
      GoogleCommonSafeRetry retrier = makeRetrier()
      int maxRetries = 10
      HttpResponseException.Builder b = new HttpResponseException.Builder(404, null, new HttpHeaders())
      GoogleJsonResponseException e = new GoogleJsonResponseException(b, null)

    when:
      Object result = retrier.doRetry(
            mockClosure, "resource", Mock(Task),
            Arrays.asList(500), Arrays.asList(404),
            new Long(-1), new Long(-1), new Long(-1), new Long(maxRetries),
            ImmutableMap.of("action", "test"), registry)
    then:
      2 * mockClosure() >> {
        throw GoogleJsonResponseExceptionFactoryTesting.newMock(
            new MockJsonFactory(), 500, "oops")
      }
      1 * mockClosure() >> {
       throw e
      }
      result == null
  }

  def "retry_until_404_not_ok"() {
    given:
      Closure  mockClosure = Mock(Closure)
      GoogleCommonSafeRetry retrier = makeRetrier()
      int maxRetries = 10
      HttpResponseException.Builder b = new HttpResponseException.Builder(404, null, new HttpHeaders())
      GoogleJsonResponseException e = new GoogleJsonResponseException(b, null)

    when:
      Object result = retrier.doRetry(
            mockClosure, "resource", Mock(Task),
            Arrays.asList(500), null,
            new Long(-1), new Long(-1), new Long(-1), new Long(maxRetries),
            ImmutableMap.of("action", "test"), registry)
    then:
      2 * mockClosure() >> {
       throw GoogleJsonResponseExceptionFactoryTesting.newMock(
            new MockJsonFactory(), 500, "oops")
      }
      1 * mockClosure() >> {
       throw e
      }
      final GoogleJsonResponseException ex = thrown()
      ex.statusCode == 404
  }
}
