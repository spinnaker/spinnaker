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

import static com.squareup.okhttp.Protocol.HTTP_1_1;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.netflix.spinnaker.clouddriver.cloudfoundry.client.HttpCloudFoundryClient.ProtobufDopplerEnvelopeConverter;
import com.squareup.okhttp.Interceptor;
import com.squareup.okhttp.MediaType;
import com.squareup.okhttp.Request;
import com.squareup.okhttp.Response;
import com.squareup.okhttp.ResponseBody;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.util.List;
import org.cloudfoundry.dropsonde.events.EventFactory.Envelope;
import org.junit.jupiter.api.Test;
import retrofit.converter.ConversionException;
import retrofit.converter.Converter;
import retrofit.mime.TypedInput;

class HttpCloudFoundryClientTest {
  @Test
  void createRetryInterceptorShouldRetryOnInternalServerErrorsThenTimeOut() {
    Request request = new Request.Builder().url("http://duke.of.url").build();
    Response response502 =
        new Response.Builder().code(502).request(request).protocol(HTTP_1_1).build();
    Response response503 =
        new Response.Builder().code(503).request(request).protocol(HTTP_1_1).build();
    Response response504 =
        new Response.Builder().code(504).request(request).protocol(HTTP_1_1).build();
    Response response200 =
        new Response.Builder().code(200).request(request).protocol(HTTP_1_1).build();
    Interceptor.Chain chain = mock(Interceptor.Chain.class);

    when(chain.request()).thenReturn(request);
    try {
      when(chain.proceed(any())).thenReturn(response502, response503, response504, response200);
    } catch (IOException e) {
      fail("Should not happen!");
    }

    HttpCloudFoundryClient cloudFoundryClient =
        new HttpCloudFoundryClient(
            "account", "appsManUri", "metricsUri", "host", "user", "password", false, 500, 16);
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
    ResponseBody body =
        ResponseBody.create(MediaType.parse("application/octet-stream"), "Bad credentials");
    Response response401 =
        new Response.Builder().code(401).request(request).body(body).protocol(HTTP_1_1).build();
    Interceptor.Chain chain = mock(Interceptor.Chain.class);

    when(chain.request()).thenReturn(request);
    try {
      when(chain.proceed(any())).thenReturn(response401);
    } catch (IOException e) {
      fail("Should not happen!");
    }

    HttpCloudFoundryClient cloudFoundryClient =
        new HttpCloudFoundryClient(
            "account", "appsManUri", "metricsUri", "host", "user", "password", false, 500, 16);
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
    Response response502 =
        new Response.Builder().code(502).request(request).protocol(HTTP_1_1).build();
    Response response200 =
        new Response.Builder().code(200).request(request).protocol(HTTP_1_1).build();
    Interceptor.Chain chain = mock(Interceptor.Chain.class);

    when(chain.request()).thenReturn(request);
    try {
      when(chain.proceed(any())).thenReturn(response502, response200);
    } catch (IOException e) {
      fail("Should not happen!");
    }

    HttpCloudFoundryClient cloudFoundryClient =
        new HttpCloudFoundryClient(
            "account", "appsManUri", "metricsUri", "host", "user", "password", false, 500, 16);
    Response response = cloudFoundryClient.createRetryInterceptor(chain);

    try {
      verify(chain, times(2)).proceed(eq(request));
    } catch (IOException e) {
      fail("Should not happen!");
    }
    assertThat(response).isEqualTo(response200);
  }

  @Test
  void protobufDopplerEnvelopeConverter_convertsMultipartResponse() throws ConversionException {
    Converter converter = new ProtobufDopplerEnvelopeConverter();

    List<Envelope> envelopes = (List<Envelope>) converter.fromBody(new TestingTypedInput(), null);

    assertThat(envelopes.size()).isEqualTo(14);
  }

  class TestingTypedInput implements TypedInput {
    private final File multipartProtobufLogs;

    TestingTypedInput() {
      ClassLoader classLoader = getClass().getClassLoader();
      try {
        multipartProtobufLogs = new File(classLoader.getResource("doppler.recent.logs").toURI());
      } catch (URISyntaxException e) {
        throw new RuntimeException(e);
      }
    }

    @Override
    public String mimeType() {
      return "multipart/x-protobuf; boundary=a7d612f5da24eb116b1c0889c112d0a1beecd7e640d921ad9210100e2f77";
    }

    @Override
    public long length() {
      return multipartProtobufLogs.length();
    }

    @Override
    public InputStream in() throws FileNotFoundException {
      return new FileInputStream(multipartProtobufLogs);
    }
  }
}
