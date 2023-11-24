/*
 * Copyright 2014 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.orca.retrofit.exceptions

import retrofit.RestAdapter
import retrofit.RetrofitError
import retrofit.client.Client
import retrofit.client.Response
import retrofit.converter.JacksonConverter
import retrofit.mime.TypedString
import spock.lang.Specification
import spock.lang.Subject
import spock.lang.Unroll
import static java.net.HttpURLConnection.HTTP_BAD_GATEWAY
import static java.net.HttpURLConnection.HTTP_UNAVAILABLE

class BaseRetrofitExceptionHandlerSpec extends Specification {
  class TestBaseRetrofitExceptionHandler extends BaseRetrofitExceptionHandler {
    @Override
    boolean handles(Exception e) {
      true
    }

    @Override
    Response handle(String taskName, Exception e) {
      String kind = null
      Integer responseCode = null
      if (e instanceof RetrofitError) {
        kind = e.kind.toString()
        responseCode = e.response?.status
      }
      boolean retry = shouldRetry(e, kind, responseCode)
      new Response(e.class.simpleName, taskName, responseDetails("test"), retry)
    }
  }

  @Subject
  def handler = new TestBaseRetrofitExceptionHandler()

  def "should not retry an internal error"() {
    given:
    def client = Stub(Client) {
      execute(_) >> { throw new IllegalArgumentException("Path parameter is null") }
    }

    and:
    def api = new RestAdapter.Builder()
      .setEndpoint("http://localhost:1337")
      .setClient(client)
      .setConverter(new JacksonConverter())
      .build()
      .create(DummyRetrofitApi)

    when:
    def ex = expectingException {
      api.getWithArg(null)
    }

    then:
    with(handler.handle("whatever", ex)) {
      !shouldRetry
    }
  }

  @Unroll
  def "should not retry a network error on a #httpMethod request"() {
    given:
    def client = Stub(Client) {
      execute(_) >> { throw new IOException("network error") }
    }

    and:
    def api = new RestAdapter.Builder()
      .setEndpoint("http://localhost:1337")
      .setClient(client)
      .setConverter(new JacksonConverter())
      .build()
      .create(DummyRetrofitApi)

    when:
    def ex = expectingException {
      api."$methodName"("whatever")
    }

    then:
    with(handler.handle("whatever", ex)) {
      !shouldRetry
    }

    where:
    httpMethod << ["POST", "PATCH"]
    methodName = httpMethod.toLowerCase()
  }

  @Unroll
  def "should retry a network error on a #httpMethod request"() {
    given:
    def client = Stub(Client) {
      execute(_) >> { throw new IOException("network error") }
    }

    and:
    def api = new RestAdapter.Builder()
      .setEndpoint("http://localhost:1337")
      .setClient(client)
      .setConverter(new JacksonConverter())
      .build()
      .create(DummyRetrofitApi)

    when:
    def ex = expectingException {
      api."$methodName"()
    }

    then:
    with(handler.handle("whatever", ex)) {
      shouldRetry
    }

    where:
    httpMethod << ["GET", "HEAD", "DELETE", "PUT"]
    methodName = httpMethod.toLowerCase()
  }

  @Unroll
  def "shouldRetry=#expectedShouldRetry an HTTP #statusCode on a #httpMethod request"() {
    given:
    def client = Stub(Client) {
      execute(_) >> new Response("http://localhost:1337", statusCode, "bad gateway", [], new TypedString(""))
    }

    and:
    def api = new RestAdapter.Builder()
      .setEndpoint("http://localhost:1337")
      .setConverter(new JacksonConverter())
      .setClient(client)
      .build()
      .create(DummyRetrofitApi)

    and:
    def ex = expectingException {
      if (httpMethod in ["POST", "PATCH"]) {
        api."$methodName"("body")
      } else {
        api."$methodName"()
      }
    }

    expect:
    with(handler.handle("whatever", ex)) {
      shouldRetry == expectedShouldRetry
    }

    where:
    httpMethod | statusCode       || expectedShouldRetry
    "POST"     | HTTP_BAD_GATEWAY || false
    "PATCH"    | HTTP_BAD_GATEWAY || false
    "POST"     | HTTP_UNAVAILABLE || true

    "GET"      | HTTP_BAD_GATEWAY || true
    "HEAD"     | HTTP_BAD_GATEWAY || true
    "DELETE"   | HTTP_BAD_GATEWAY || true
    "PUT"      | HTTP_BAD_GATEWAY || true


    methodName = httpMethod.toLowerCase()
  }

  private static RetrofitError expectingException(Closure action) {
    try {
      action()
      throw new IllegalStateException("Closure did not throw an exception")
    } catch (RetrofitError e) {
      return e
    }
  }
}
