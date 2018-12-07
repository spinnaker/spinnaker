/*
 * Copyright 2018 Pivotal, Inc.
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

package com.netflix.spinnaker.clouddriver.cloudfoundry.client;

import com.squareup.okhttp.Interceptor;
import com.squareup.okhttp.MediaType;
import com.squareup.okhttp.Request;
import com.squareup.okhttp.Response;
import com.squareup.okhttp.ResponseBody;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static com.squareup.okhttp.Protocol.HTTP_1_1;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.*;

class HttpCloudFoundryClientTest {
  @Test
  void createRetryInterceptorShouldRetryOnInternalServerErrorsThenTimeOut() {
    Request request = new Request.Builder().url("http://duke.of.url").build();
    Response response502 = new Response.Builder().code(502).request(request).protocol(HTTP_1_1).build();
    Response response503 = new Response.Builder().code(503).request(request).protocol(HTTP_1_1).build();
    Response response504 = new Response.Builder().code(504).request(request).protocol(HTTP_1_1).build();
    Response response200 = new Response.Builder().code(200).request(request).protocol(HTTP_1_1).build();
    Interceptor.Chain chain = mock(Interceptor.Chain.class);

    when(chain.request()).thenReturn(request);
    try {
      when(chain.proceed(any())).thenReturn(response502, response503, response504, response200);
    } catch (IOException e) {
      fail("Should not happen!");
    }

    HttpCloudFoundryClient cloudFoundryClient = new HttpCloudFoundryClient("account", "uri", "host", "user", "password");
    Response response = cloudFoundryClient.createRetryInterceptor(chain);

    try {
      verify(chain, times(3)).proceed(eq(request));
    } catch (IOException e) {
      fail("Should not happen!");
    }
    assertThat(response).isEqualTo(response504);
  }

  @Test
  void createRetryInterceptorShouldNotRefreshTokenOnBadCredentials() {
    Request request = new Request.Builder().url("http://duke.of.url").build();
    ResponseBody body = ResponseBody.create(MediaType.parse("application/octet-stream"), "Bad credentials");
    Response response401 = new Response.Builder().code(401).request(request).body(body).protocol(HTTP_1_1).build();
    Interceptor.Chain chain = mock(Interceptor.Chain.class);

    when(chain.request()).thenReturn(request);
    try {
      when(chain.proceed(any())).thenReturn(response401);
    } catch (IOException e) {
      fail("Should not happen!");
    }

    HttpCloudFoundryClient cloudFoundryClient = new HttpCloudFoundryClient("account", "uri", "host", "user", "password");
    Response response = cloudFoundryClient.createRetryInterceptor(chain);

    try {
      verify(chain, times(1)).proceed(eq(request));
    } catch (IOException e) {
      fail("Should not happen!");
    }
    assertThat(response).isEqualTo(response401);
  }

  @Test
  void createRetryInterceptorShouldReturnOnEverythingElse() {
    Request request = new Request.Builder().url("http://duke.of.url").build();
    Response response502 = new Response.Builder().code(502).request(request).protocol(HTTP_1_1).build();
    Response response200 = new Response.Builder().code(200).request(request).protocol(HTTP_1_1).build();
    Interceptor.Chain chain = mock(Interceptor.Chain.class);

    when(chain.request()).thenReturn(request);
    try {
      when(chain.proceed(any())).thenReturn(response502, response200);
    } catch (IOException e) {
      fail("Should not happen!");
    }

    HttpCloudFoundryClient cloudFoundryClient = new HttpCloudFoundryClient("account", "uri", "host", "user", "password");
    Response response = cloudFoundryClient.createRetryInterceptor(chain);

    try {
      verify(chain, times(2)).proceed(eq(request));
    } catch (IOException e) {
      fail("Should not happen!");
    }
    assertThat(response).isEqualTo(response200);
  }
}
