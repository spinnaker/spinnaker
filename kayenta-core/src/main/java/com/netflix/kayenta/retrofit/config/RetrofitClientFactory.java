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

package com.netflix.kayenta.retrofit.config;

import static retrofit.Endpoints.newFixedEndpoint;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spinnaker.kork.annotations.VisibleForTesting;
import com.netflix.spinnaker.kork.retrofit.exceptions.SpinnakerRetrofitErrorHandler;
import com.netflix.spinnaker.retrofit.Slf4jRetrofitLogger;
import com.squareup.okhttp.Authenticator;
import com.squareup.okhttp.Credentials;
import com.squareup.okhttp.OkHttpClient;
import com.squareup.okhttp.Request;
import com.squareup.okhttp.Response;
import java.io.IOException;
import java.net.Proxy;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.function.Function;
import org.apache.commons.lang3.StringUtils;
import org.apache.tomcat.util.codec.binary.Base64;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;
import retrofit.Endpoint;
import retrofit.RestAdapter;
import retrofit.client.OkClient;
import retrofit.converter.Converter;
import retrofit.converter.JacksonConverter;

@Component
public class RetrofitClientFactory {

  @Value("${retrofit.log-level:BASIC}")
  @VisibleForTesting
  public String retrofitLogLevel;

  @VisibleForTesting
  public Function<Class<?>, Slf4jRetrofitLogger> createRetrofitLogger = Slf4jRetrofitLogger::new;

  @Bean
  JacksonConverter jacksonConverterWithMapper(ObjectMapper objectMapper) {
    return new JacksonConverter(objectMapper);
  }

  public <T> T createClient(
      Class<T> type, Converter converter, RemoteService remoteService, OkHttpClient okHttpClient) {
    try {
      return createClient(type, converter, remoteService, okHttpClient, null, null, null, null);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  public <T> T createClient(
      Class<T> type,
      Converter converter,
      RemoteService remoteService,
      OkHttpClient okHttpClient,
      String username,
      String password,
      String usernamePasswordFile) {
    try {
      return createClient(
          type,
          converter,
          remoteService,
          okHttpClient,
          username,
          password,
          usernamePasswordFile,
          null);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  public <T> T createClient(
      Class<T> type,
      Converter converter,
      RemoteService remoteService,
      OkHttpClient okHttpClient,
      String username,
      String password,
      String usernamePasswordFile,
      String bearerToken)
      throws IOException {
    String baseUrl = remoteService.getBaseUrl();

    baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;

    Endpoint endpoint = newFixedEndpoint(baseUrl);

    if (!(StringUtils.isEmpty(username)
        && StringUtils.isEmpty(password)
        && StringUtils.isEmpty(usernamePasswordFile)
        && StringUtils.isEmpty(bearerToken))) {
      okHttpClient =
          createAuthenticatedClient(username, password, usernamePasswordFile, bearerToken);
    }

    Slf4jRetrofitLogger logger = createRetrofitLogger.apply(type);

    return new RestAdapter.Builder()
        .setEndpoint(endpoint)
        .setClient(new OkClient(okHttpClient))
        .setConverter(converter)
        .setErrorHandler(SpinnakerRetrofitErrorHandler.getInstance())
        .setLogLevel(RestAdapter.LogLevel.valueOf(retrofitLogLevel))
        .setLog(logger)
        .build()
        .create(type);
  }

  private static OkHttpClient createAuthenticatedClient(
      String username, String password, String usernamePasswordFile, String bearerToken)
      throws IOException {
    final String credential;

    if (StringUtils.isNotEmpty(usernamePasswordFile)) {
      String trimmedFileContent =
          new String(Files.readAllBytes(Paths.get(usernamePasswordFile))).trim();

      credential = "Basic " + Base64.encodeBase64String(trimmedFileContent.getBytes());
    } else if (StringUtils.isNotEmpty(bearerToken)) {
      credential = "Bearer " + bearerToken;
    } else {
      credential = Credentials.basic(username, password);
    }

    OkHttpClient httpClient = new OkHttpClient();

    httpClient.setAuthenticator(
        new Authenticator() {
          @Override
          public Request authenticate(Proxy proxy, Response response) throws IOException {
            return response.request().newBuilder().header("Authorization", credential).build();
          }

          @Override
          public Request authenticateProxy(Proxy proxy, Response response) throws IOException {
            return response
                .request()
                .newBuilder()
                .header("Proxy-Authorization", credential)
                .build();
          }
        });

    return httpClient;
  }
}
