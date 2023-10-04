/*
 * Copyright 2020 Playtika.
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

package com.netflix.kayenta.prometheus.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableMap;
import com.netflix.kayenta.prometheus.config.PrometheusResponseConverter;
import com.netflix.kayenta.prometheus.model.PrometheusResults;
import com.netflix.kayenta.retrofit.config.RemoteService;
import com.netflix.kayenta.retrofit.config.RetrofitClientFactory;
import com.netflix.spinnaker.kork.retrofit.exceptions.SpinnakerServerException;
import com.squareup.okhttp.OkHttpClient;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Scanner;
import lombok.SneakyThrows;
import org.apache.commons.lang3.StringUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockserver.client.MockServerClient;
import org.mockserver.model.MediaType;
import org.mockserver.netty.MockServer;

public class PrometheusRemoteServiceTest {
  public MockServer mockServer;

  private MockServerClient mockServerClient;

  RetrofitClientFactory retrofitClientFactory = getRetrofitClientFactory();
  ObjectMapper objectMapper = new ObjectMapper();
  PrometheusResponseConverter prometheusConverter = new PrometheusResponseConverter(objectMapper);
  OkHttpClient okHttpClient = new OkHttpClient();
  PrometheusRemoteService prometheusRemoteService;

  @BeforeEach
  public void setUp() {
    mockServer = new MockServer();
    mockServerClient = new MockServerClient("localhost", mockServer.getPort());
    prometheusRemoteService = createClient(mockServerClient.getPort());
  }

  @AfterEach
  public void cleanup() {
    mockServer.close();
    mockServer.stop();
  }

  @Test
  public void isHealthyReturnsOk() {
    mockServerClient
        .when(request().withMethod("GET").withPath("/-/healthy"))
        .respond(response().withStatusCode(200).withBody("Prometheus is healthy"));

    String response = prometheusRemoteService.isHealthy();

    assertThat(response).isEqualTo("Prometheus is healthy");
  }

  @Test
  public void isHealthyReturnsInternalServerError() {
    mockServerClient
        .when(request().withMethod("GET").withPath("/-/healthy"))
        .respond(response().withStatusCode(500));

    assertThatThrownBy(() -> prometheusRemoteService.isHealthy())
        .isInstanceOf(SpinnakerServerException.class)
        .hasMessageContaining("Internal Server Error");
  }

  @Test
  public void rangeQueryWithoutTags() {
    String query = "any-query";
    String start = "2020-09-09T13:12:56.540500Z";
    String end = "2020-09-09T13:13:56.540500Z";
    String response = getFile("/prometheus/rangeQueryWithoutTags.json");
    mockServerClient
        .when(request().withMethod("GET").withPath("/api/v1/query_range.*"))
        .respond(response().withStatusCode(200).withBody(response, MediaType.APPLICATION_JSON));

    List<PrometheusResults> results = prometheusRemoteService.rangeQuery(query, start, end, 5L);

    assertThat(results)
        .containsExactly(
            PrometheusResults.builder()
                .startTimeMillis(1599657179540L)
                .endTimeMillis(1599657237540L)
                .stepSecs(1L)
                .tags(Collections.emptyMap())
                .values(
                    Arrays.asList(
                        43.0, 41.0, 41.0, 43.0, 44.0, 40.0, 40.0, 43.0, 42.0, 43.0, 41.0, 43.0,
                        44.0, 43.0, 44.0, 40.0, 40.0, 43.0, 44.0, 42.0, 41.0, 43.0, 40.0, 42.0,
                        44.0, 40.0, 42.0, 40.0, 43.0, 42.0, 41.0, 43.0, 40.0, 42.0, 44.0, 42.0,
                        43.0, 44.0, 43.0, 42.0, 41.0, 41.0, 43.0, 41.0, 41.0, 42.0, 44.0, 44.0,
                        42.0, 41.0, 43.0, 40.0, 44.0, 43.0, 44.0, 42.0, 42.0, 43.0))
                .build());
  }

  @Test
  public void rangeQueryWithTags() {
    String query = "any-query";
    String start = "2020-09-09T13:12:56.540500Z";
    String end = "2020-09-09T13:13:56.540500Z";
    String response = getFile("/prometheus/rangeQueryWithTags.json");
    mockServerClient
        .when(request().withMethod("GET").withPath("/api/v1/query_range.*"))
        .respond(response().withStatusCode(200).withBody(response, MediaType.APPLICATION_JSON));

    List<PrometheusResults> results = prometheusRemoteService.rangeQuery(query, start, end, 5L);

    assertThat(results)
        .containsExactly(
            PrometheusResults.builder()
                .startTimeMillis(1599658195474L)
                .endTimeMillis(1599658252474L)
                .stepSecs(1L)
                .tags(
                    ImmutableMap.of(
                        "exception", "InternalServerError", "method", "GET", "uri", "/uri-1"))
                .values(
                    Arrays.asList(
                        4.0, 4.0, 6.0, 2.0, 4.0, 4.0, 4.0, 5.0, 5.0, 9.0, 1.0, 1.0, 1.0, 6.0, 6.0,
                        5.0, 3.0, 5.0, 4.0, 3.0, 2.0, 7.0, 7.0, 7.0, 8.0, 3.0, 2.0, 8.0, 3.0, 8.0,
                        1.0, 3.0, 9.0, 7.0, 6.0, 8.0, 2.0, 8.0, 9.0, 2.0, 9.0, 6.0, 3.0, 6.0, 1.0,
                        2.0, 5.0, 1.0, 1.0, 7.0, 2.0, 4.0, 1.0, 5.0, 1.0, 8.0, 2.0))
                .build());
  }

  @Test
  public void rangeQueryEmpty() {
    String query = "any-query";
    String start = "2020-09-09T13:12:56.540500Z";
    String end = "2020-09-09T13:13:56.540500Z";
    String response = getFile("/prometheus/rangeQueryEmpty.json");
    mockServerClient
        .when(request().withMethod("GET").withPath("/api/v1/query_range.*"))
        .respond(response().withStatusCode(200).withBody(response, MediaType.APPLICATION_JSON));

    List<PrometheusResults> results = prometheusRemoteService.rangeQuery(query, start, end, 5L);

    assertThat(results).isNull();
  }

  @SneakyThrows
  public static String getFile(String name) {
    java.net.URL url = PrometheusRemoteServiceTest.class.getResource(name);
    String text =
        new Scanner(new File(url.toURI()), StandardCharsets.UTF_8.name())
            .useDelimiter("\\Z")
            .next();
    return StringUtils.replace(text, "\n", "");
  }

  private RetrofitClientFactory getRetrofitClientFactory() {
    RetrofitClientFactory factory = new RetrofitClientFactory();
    factory.retrofitLogLevel = "BASIC";
    return factory;
  }

  private RemoteService getRemoteService(int port) {
    RemoteService remoteService = new RemoteService();
    remoteService.setBaseUrl("http://localhost:" + port);
    return remoteService;
  }

  @SneakyThrows
  private PrometheusRemoteService createClient(Integer port) {
    return retrofitClientFactory.createClient(
        PrometheusRemoteService.class,
        prometheusConverter,
        getRemoteService(port),
        okHttpClient,
        null,
        null,
        null);
  }
}
