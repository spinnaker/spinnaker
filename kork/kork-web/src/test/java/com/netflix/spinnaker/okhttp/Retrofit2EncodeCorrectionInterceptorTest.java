/*
 * Copyright 2025 OpsMx, Inc.
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

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.ok;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import com.github.tomakehurst.wiremock.verification.LoggedRequest;
import com.netflix.spectator.api.NoopRegistry;
import com.netflix.spinnaker.config.DefaultServiceEndpoint;
import com.netflix.spinnaker.config.OkHttpClientComponents;
import com.netflix.spinnaker.config.ServiceEndpoint;
import com.netflix.spinnaker.config.okhttp3.DefaultOkHttpClientBuilderProvider;
import com.netflix.spinnaker.config.okhttp3.OkHttpClientProvider;
import com.netflix.spinnaker.config.okhttp3.RawOkHttpClientConfiguration;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.stream.Stream;
import okhttp3.OkHttpClient;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.task.TaskExecutorBuilder;
import org.springframework.boot.test.context.SpringBootTest;
import retrofit2.Call;
import retrofit2.Retrofit;
import retrofit2.converter.jackson.JacksonConverterFactory;
import retrofit2.http.GET;
import retrofit2.http.Path;
import retrofit2.http.Query;

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.NONE,
    classes = {
      OkHttpClientComponents.class,
      RawOkHttpClientConfiguration.class,
      OkHttpClientProvider.class,
      DefaultOkHttpClientBuilderProvider.class,
      Retrofit2EncodeCorrectionInterceptor.class,
      TaskExecutorBuilder.class,
      NoopRegistry.class
    })
public class Retrofit2EncodeCorrectionInterceptorTest {

  // to test if a path parameter value containing various special characters is handled correctly
  // with Retrofit2
  private static final String PATH_VAL = "path: [] () {} + ^ < > ` = % $ & # @ ; , ? \" ' end path";
  private static final String ENCODED_PATH_VAL = encodedString(PATH_VAL);

  // to test if a query parameter value containing various special characters is handled correctly
  // with Retrofit2
  private static final String QUERY_PARAM1 =
      "qry1: [] () {} + ^ < > ` = $ & # @ ; , ? \" ' /end qry1";
  private static final String ENCODED_QUERY_PARAM1 = encodedString(QUERY_PARAM1);

  private static final String QUERY_PARAM2 = "qry2: [] () + = $ & # @ ; , ? \" ' end qry2";
  private static final String ENCODED_QUERY_PARAM2 = encodedString(QUERY_PARAM2);
  private static final String QUERY_PARAM3 = "";
  private static final String EXPECTED_URL =
      "/test/"
          + ENCODED_PATH_VAL
          + "/get?qry1="
          + ENCODED_QUERY_PARAM1
          + "&qry2="
          + ENCODED_QUERY_PARAM2
          + "&qry3=";
  private static ServiceEndpoint endpoint;

  @RegisterExtension
  static WireMockExtension wireMock =
      WireMockExtension.newInstance().options(wireMockConfig().dynamicPort()).build();

  @Autowired OkHttpClientProvider okHttpClientProvider;

  @BeforeAll
  public static void setup() {
    wireMock.stubFor(get(EXPECTED_URL).willReturn(ok()));
    endpoint = new DefaultServiceEndpoint("test", wireMock.baseUrl());
  }

  @Test
  public void testWithoutEncodingCorrection() throws IOException {
    OkHttpClient okHttpClient =
        removeEncodeCorrectionInterceptor(okHttpClientProvider.getClient(endpoint));

    Retrofit2Service service = getRetrofit2Service(endpoint.getBaseUrl(), okHttpClient);
    service.getRequest(PATH_VAL, QUERY_PARAM1, QUERY_PARAM2, QUERY_PARAM3).execute();
    LoggedRequest requestReceived = wireMock.getAllServeEvents().get(0).getRequest();
    // note the partial encoding done by Retrofit2
    assertThat(getPathFromUrl(requestReceived.getUrl()))
        .isEqualTo(
            "/test/path:%20[]%20()%20%7B%7D%20+%20%5E%20%3C%20%3E%20%60%20=%20%25%20$%20&%20%23%20@%20;%20,%20%3F%20%22%20'%20end%20path/get");
    System.out.println(requestReceived.getUrl());
    String url = requestReceived.getUrl();
    String[] queryParams = getQueryParamsFromUrl(url);
    // query parameters are partially encoded by Retrofit2
    assertThat(queryParams[0])
        .isEqualTo(
            "qry1:%20[]%20()%20{}%20+%20^%20%3C%20%3E%20`%20%3D%20$%20%26%20%23%20@%20;%20,%20?%20%22%20%27%20/end%20qry1");
    assertThat(queryParams[1])
        .isEqualTo(
            "qry2:%20[]%20()%20+%20%3D%20$%20%26%20%23%20@%20;%20,%20?%20%22%20%27%20end%20qry2");
    assertThat(queryParams[2]).isEqualTo("");

    wireMock.verify(0, getRequestedFor(urlEqualTo(EXPECTED_URL)));
  }

  @Test
  public void testWithEncodingCorrection() throws IOException {
    OkHttpClient okHttpClient = okHttpClientProvider.getClient(endpoint);
    Retrofit2Service service = getRetrofit2Service(endpoint.getBaseUrl(), okHttpClient);
    service.getRequest(PATH_VAL, QUERY_PARAM1, QUERY_PARAM2, QUERY_PARAM3).execute();
    wireMock.verify(getRequestedFor(urlEqualTo(EXPECTED_URL)));
    LoggedRequest requestReceived = wireMock.getAllServeEvents().get(0).getRequest();
    System.out.println(requestReceived.getUrl());
    String url = requestReceived.getUrl();
    String[] queryParams = getQueryParamsFromUrl(url);
    assertThat(queryParams[0]).isEqualTo(ENCODED_QUERY_PARAM1);
    assertThat(queryParams[1]).isEqualTo(ENCODED_QUERY_PARAM2);
    assertThat(queryParams[2]).isEqualTo("");
  }

  @Test
  public void testRepeatingQueryParams() throws IOException {
    ObjectMapper objectMapper = new ObjectMapper();
    List<String> qry1List =
        List.of("/*/action", "/*/build", "/*/property[not(parameterDefinition)");
    String qry2 = "qryVal2";
    List<String> qry3List = List.of("foo", "bar");
    String expectedUrl =
        "/test/get?qry1="
            + encodedString("/*/action")
            + "&qry1="
            + encodedString("/*/build")
            + "&qry1="
            + encodedString("/*/property[not(parameterDefinition)")
            + "&qry2="
            + encodedString("qryVal2")
            + "&qry3="
            + encodedString("foo")
            + "&qry3="
            + encodedString("bar");

    List<String> expectedResponse =
        Stream.concat(Stream.concat(qry1List.stream(), qry3List.stream()), Stream.of(qry2))
            .toList();

    // return all the received query param values as list
    wireMock.stubFor(
        get(expectedUrl)
            .willReturn(aResponse().withBody(objectMapper.writeValueAsBytes(expectedResponse))));

    OkHttpClient okHttpClient = okHttpClientProvider.getClient(endpoint);
    QueryParamTestService service =
        new Retrofit.Builder()
            .baseUrl(endpoint.getBaseUrl())
            .client(okHttpClient)
            .addConverterFactory(JacksonConverterFactory.create())
            .build()
            .create(QueryParamTestService.class);

    List<String> result =
        service.getQueryParamTestRequest(qry1List, qry2, qry3List).execute().body();

    assertThat(result).hasSize(6);
    assertThat(result).containsAll(expectedResponse);
  }

  @Test
  public void testRepeatingQueryParams_withinUrl() throws IOException {
    ObjectMapper objectMapper = new ObjectMapper();
    List<String> qry1List =
        List.of("/*/action", "/*/build", "/*/property[not(parameterDefinition)");
    String qry2 = "qryVal2";
    List<String> qry3List = List.of("foo", "bar");
    String expectedUrl =
        "/test/get?qry1="
            + encodedString("/*/action")
            + "&qry1="
            + encodedString("/*/build")
            + "&qry1="
            + encodedString("/*/property[not(parameterDefinition)")
            + "&qry2="
            + encodedString("qryVal2")
            + "&qry3="
            + encodedString("foo")
            + "&qry3="
            + encodedString("bar");

    List<String> expectedResponse =
        Stream.concat(Stream.concat(qry1List.stream(), qry3List.stream()), Stream.of(qry2))
            .toList();

    // return all the received query param values as list
    wireMock.stubFor(
        get(expectedUrl)
            .willReturn(aResponse().withBody(objectMapper.writeValueAsBytes(expectedResponse))));

    OkHttpClient okHttpClient = okHttpClientProvider.getClient(endpoint);
    QueryParamTestService service =
        new Retrofit.Builder()
            .baseUrl(endpoint.getBaseUrl())
            .client(okHttpClient)
            .addConverterFactory(JacksonConverterFactory.create())
            .build()
            .create(QueryParamTestService.class);

    List<String> result = service.getQueryParamTestRequest2().execute().body();

    assertThat(result).hasSize(6);
    assertThat(result).containsAll(expectedResponse);
  }

  private Retrofit2Service getRetrofit2Service(String baseUrl, OkHttpClient okHttpClient) {

    return new Retrofit.Builder()
        .baseUrl(baseUrl)
        .client(okHttpClient)
        .build()
        .create(Retrofit2Service.class);
  }

  private static String encodedString(String input) {
    // after encode(), replace  '+' characters with '%20' as encode() replaces spaces with '+'
    // eg: '[ + ]' is encoded as '%5B+%2B+%5D', so replacing '+' with '%20' completes
    // the encoding
    return URLEncoder.encode(input, StandardCharsets.UTF_8).replaceAll("\\+", "%20");
  }

  private static String getPathFromUrl(String url) {
    return url.substring(0, url.indexOf('?'));
  }

  private static String[] getQueryParamsFromUrl(String url) {
    String[] params = new String[3];
    params[0] = url.substring(url.indexOf("qry1=") + 5, url.indexOf("&qry2="));
    params[1] = url.substring(url.indexOf("qry2=") + 5, url.indexOf("&qry3="));
    params[2] = url.substring(url.indexOf("qry3=") + 5);
    return params;
  }

  private OkHttpClient removeEncodeCorrectionInterceptor(OkHttpClient okHttpClient) {
    OkHttpClient.Builder builder = okHttpClient.newBuilder();
    builder.interceptors().clear();

    okHttpClient.interceptors().stream()
        .filter(interceptor -> !(interceptor instanceof Retrofit2EncodeCorrectionInterceptor))
        .forEach(builder::addInterceptor);

    return builder.build();
  }

  interface Retrofit2Service {
    @GET("test/{path}/get")
    Call<Void> getRequest(
        @Path("path") String path,
        @Query(value = "qry1", encoded = true) String qry1,
        @Query(value = "qry2", encoded = true) String qry2,
        @Query(value = "qry3", encoded = true) String qry3);
  }

  interface QueryParamTestService {
    @GET("test/get")
    Call<List<String>> getQueryParamTestRequest(
        @Query(value = "qry1", encoded = true) List<String> qry1,
        @Query(value = "qry2", encoded = true) String qry2,
        @Query(value = "qry3", encoded = true) List<String> qry3);

    @GET(
        "test/get?qry1=/*/action&qry1=/*/build&qry1=/*/property[not(parameterDefinition)&qry2=qryVal2&qry3=foo&qry3=bar")
    Call<List<String>> getQueryParamTestRequest2();
  }
}
