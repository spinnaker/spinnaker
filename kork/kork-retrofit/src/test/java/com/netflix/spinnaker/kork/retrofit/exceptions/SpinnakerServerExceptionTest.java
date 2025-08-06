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

import okhttp3.Request;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;

class SpinnakerServerExceptionTest {

  @Test
  void testSpinnakerNetworkExceptionWithUrl() {
    Throwable cause = new Throwable("arbitrary message");
    String url = "http://some-url/";
    Request request = new Request.Builder().url(url).build();
    SpinnakerNetworkException spinnakerNetworkException =
        new SpinnakerNetworkException(cause, request);
    assertThat(spinnakerNetworkException.getUrl()).isEqualTo(url);
    assertThat(spinnakerNetworkException.getHttpMethod()).isEqualTo(HttpMethod.GET.toString());
  }

  @Test
  void testSpinnakerNetworkExceptionWithSpecificMethod() {
    Throwable cause = new Throwable("arbitrary message");
    String url = "http://some-url/";
    Request request =
        new Request.Builder().url(url).method(HttpMethod.DELETE.toString(), null).build();
    SpinnakerNetworkException spinnakerNetworkException =
        new SpinnakerNetworkException(cause, request);
    assertThat(spinnakerNetworkException.getUrl()).isEqualTo(url);
    assertThat(spinnakerNetworkException.getHttpMethod()).isEqualTo(HttpMethod.DELETE.toString());
  }

  @Test
  void testSpinnakerServerExceptionWithUrl() {
    Throwable cause = new Throwable("arbitrary message");
    String url = "http://some-url/";
    Request request = new Request.Builder().url(url).build();
    SpinnakerServerException spinnakerServerException =
        new SpinnakerServerException(cause, request);
    assertThat(spinnakerServerException.getUrl()).isEqualTo(url);
    assertThat(spinnakerServerException.getHttpMethod()).isEqualTo(HttpMethod.GET.toString());
  }

  @Test
  void testSpinnakerServerExceptionWithSpecificMethod() {
    Throwable cause = new Throwable("arbitrary message");
    String url = "http://some-url/";
    Request request =
        new Request.Builder().url(url).method(HttpMethod.DELETE.toString(), null).build();
    SpinnakerServerException spinnakerServerException =
        new SpinnakerServerException(cause, request);
    assertThat(spinnakerServerException.getUrl()).isEqualTo(url);
    assertThat(spinnakerServerException.getHttpMethod()).isEqualTo(HttpMethod.DELETE.toString());
  }
}
