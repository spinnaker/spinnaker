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

import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.ok;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;

import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.matching.RequestPatternBuilder;
import com.netflix.spinnaker.kork.common.Header;
import com.netflix.spinnaker.security.AuthenticatedRequest;
import java.util.Map;
import okhttp3.OkHttpClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import retrofit2.Call;
import retrofit2.Retrofit;
import retrofit2.http.GET;

class SpinnakerRequestHeaderInterceptorTest {

  public static final String REQUEST_PATH = "/foo";

  public static final String TEST_USER = "some-user";

  public static final String TEST_ACCOUNTS = "some-accounts";

  public static final String TEST_REQUEST_ID = "some-request-id";

  public static final Map<Header, String> TEST_SPINNAKER_HEADERS =
      Map.of(
          Header.USER,
          TEST_USER,
          Header.ACCOUNTS,
          TEST_ACCOUNTS,
          Header.REQUEST_ID,
          TEST_REQUEST_ID);

  /**
   * Use this instead of annotating the class with @WireMockTest so there's a WireMock object
   * available for parameterized tests. Otherwise the arguments from e.g. ValueSource compete with
   * the WireMockRuntimeInfo argument that @WireMockTest provides.
   */
  @RegisterExtension
  static WireMockExtension wireMock =
      WireMockExtension.newInstance().options(wireMockConfig().dynamicPort()).build();

  @BeforeEach
  void setup(TestInfo testInfo, WireMockRuntimeInfo wmRuntimeInfo) {
    System.out.println("--------------- Test " + testInfo.getDisplayName());

    // set up an arbitrary response to avoid 404s, and so it's possible to
    // verify the request headers wiremock receives.
    wireMock.stubFor(get(REQUEST_PATH).willReturn(ok()));
  }

  @AfterEach
  void cleanup() {
    AuthenticatedRequest.clear();
  }

  @ParameterizedTest(name = "propagateSpinnakerHeaders = {0}")
  @ValueSource(booleans = {false, true})
  void propagateSpinnakerHeaders(boolean propagateSpinnakerHeaders) throws Exception {
    SpinnakerRequestHeaderInterceptor spinnakerRequestHeaderInterceptor =
        new SpinnakerRequestHeaderInterceptor(propagateSpinnakerHeaders);

    RetrofitService retrofitService =
        makeRetrofitService(wireMock.baseUrl(), spinnakerRequestHeaderInterceptor);

    // Add some spinnaker headers to the MDC
    TEST_SPINNAKER_HEADERS.forEach(AuthenticatedRequest::set);

    // Make a request
    retrofitService.getRequest().execute();

    // Verify that wiremock did/didn't receive the spinnaker headers as appropriate
    TEST_SPINNAKER_HEADERS.forEach(
        (Header header, String value) -> {
          RequestPatternBuilder requestPatternBuilder =
              getRequestedFor(urlPathEqualTo(REQUEST_PATH));
          if (propagateSpinnakerHeaders) {
            requestPatternBuilder.withHeader(header.getHeader(), equalTo(value));
          } else {
            requestPatternBuilder.withoutHeader(header.getHeader());
          }
          wireMock.verify(requestPatternBuilder);
        });
  }

  @Test
  void skipAccountHeaders() throws Exception {
    SpinnakerRequestHeaderInterceptor spinnakerRequestHeaderInterceptor =
        new SpinnakerRequestHeaderInterceptor(
            true /* propagateSpinnakerHeaders */, true /* skipAccountsHeader */);

    RetrofitService retrofitService =
        makeRetrofitService(wireMock.baseUrl(), spinnakerRequestHeaderInterceptor);

    // Add some spinnaker headers to the MDC, including the accounts header
    assertThat(TEST_SPINNAKER_HEADERS).containsKey(Header.ACCOUNTS);
    TEST_SPINNAKER_HEADERS.forEach(AuthenticatedRequest::set);

    // Make a request
    retrofitService.getRequest().execute();

    // Verify that wiremock received all spinnaker headers except the accounts header
    TEST_SPINNAKER_HEADERS.forEach(
        (Header header, String value) -> {
          RequestPatternBuilder requestPatternBuilder =
              getRequestedFor(urlPathEqualTo(REQUEST_PATH));
          if (Header.ACCOUNTS.equals(header)) {
            requestPatternBuilder.withoutHeader(header.getHeader());
          } else {
            requestPatternBuilder.withHeader(header.getHeader(), equalTo(value));
          }
          wireMock.verify(requestPatternBuilder);
        });
  }

  private RetrofitService makeRetrofitService(
      String baseUrl, SpinnakerRequestHeaderInterceptor spinnakerRequestHeaderInterceptor) {
    OkHttpClient okHttpClient =
        new OkHttpClient.Builder().addInterceptor(spinnakerRequestHeaderInterceptor).build();

    return new Retrofit.Builder()
        .baseUrl(baseUrl)
        .client(okHttpClient)
        .build()
        .create(RetrofitService.class);
  }

  interface RetrofitService {
    @GET(REQUEST_PATH)
    Call<Void> getRequest();
  }
}
