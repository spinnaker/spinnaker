/*
 * Copyright 2023 Salesforce, Inc.
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

package com.netflix.spinnaker.kork.retrofit.exceptions;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.gson.Gson;
import com.netflix.spinnaker.kork.exceptions.SpinnakerException;
import java.io.IOException;
import java.util.List;
import okhttp3.Request;
import org.junit.jupiter.api.Test;
import retrofit.RetrofitError;
import retrofit.client.Response;
import retrofit.converter.ConversionException;
import retrofit.converter.GsonConverter;
import retrofit.mime.TypedByteArray;

class SpinnakerServerExceptionTest {
  private static final String CUSTOM_MESSAGE = "custom message";

  @Test
  void testSpinnakerNetworkExceptionWithUrl() {
    Throwable cause = new Throwable("arbitrary message");
    String url = "http://some-url/";
    Request request = new Request.Builder().url(url).build();
    SpinnakerNetworkException spinnakerNetworkException =
        new SpinnakerNetworkException(cause, request);
    assertThat(spinnakerNetworkException.getUrl()).isEqualTo(url);
  }

  @Test
  void testSpinnakerNetworkException_NewInstance() {
    IOException initialException = new IOException("message");
    String url = "http://localhost";
    try {
      RetrofitError error = RetrofitError.networkError(url, initialException);
      throw new SpinnakerNetworkException(error);
    } catch (SpinnakerException e) {
      SpinnakerException newException = e.newInstance(CUSTOM_MESSAGE);

      assertThat(newException).isInstanceOf(SpinnakerNetworkException.class);
      assertThat(newException).hasMessage(CUSTOM_MESSAGE);
      assertThat(newException).hasCause(e);
      SpinnakerNetworkException spinnakerNetworkException =
          (SpinnakerNetworkException) newException;
      assertThat(spinnakerNetworkException.getUrl()).isEqualTo(url);
    }
  }

  @Test
  void testSpinnakerServerExceptionWithUrl() {
    Throwable cause = new Throwable("arbitrary message");
    String url = "http://some-url/";
    Request request = new Request.Builder().url(url).build();
    SpinnakerServerException spinnakerServerException =
        new SpinnakerServerException(cause, request);
    assertThat(spinnakerServerException.getUrl()).isEqualTo(url);
  }

  @Test
  void testSpinnakerServerException_NewInstance() {
    Throwable cause = new Throwable("message");
    String url = "http://localhost";
    try {
      RetrofitError error = RetrofitError.unexpectedError(url, cause);
      throw new SpinnakerServerException(error);
    } catch (SpinnakerException e) {
      SpinnakerException newException = e.newInstance(CUSTOM_MESSAGE);

      assertThat(newException).isInstanceOf(SpinnakerServerException.class);
      assertThat(newException).hasMessage(CUSTOM_MESSAGE);
      assertThat(newException).hasCause(e);
      SpinnakerServerException spinnakerServerException = (SpinnakerServerException) newException;
      assertThat(spinnakerServerException.getUrl()).isEqualTo(url);
    }
  }

  @Test
  void testSpinnakerConversionException_NewInstance() {
    String url = "http://localhost";
    String reason = "reason";

    try {
      Response response =
          new Response(
              url,
              200,
              reason,
              List.of(),
              new TypedByteArray("application/json", "message".getBytes()));
      ConversionException conversionException =
          new ConversionException("message", new Throwable(reason));

      RetrofitError retrofitError =
          RetrofitError.conversionError(
              url, response, new GsonConverter(new Gson()), null, conversionException);
      throw new SpinnakerConversionException(retrofitError);
    } catch (SpinnakerException e) {
      SpinnakerException newException = e.newInstance(CUSTOM_MESSAGE);

      assertThat(newException).isInstanceOf(SpinnakerConversionException.class);
      assertThat(newException).hasMessage(CUSTOM_MESSAGE);
      assertThat(newException).hasCause(e);
      SpinnakerConversionException spinnakerConversionException =
          (SpinnakerConversionException) newException;
      assertThat(spinnakerConversionException.getRetryable()).isNotNull();
      assertThat(spinnakerConversionException.getRetryable()).isFalse();
      assertThat(spinnakerConversionException.getUrl()).isEqualTo(url);
    }
  }
}
