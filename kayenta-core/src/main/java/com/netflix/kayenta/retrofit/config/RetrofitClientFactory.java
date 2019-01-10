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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spinnaker.retrofit.Slf4jRetrofitLogger;
import com.squareup.okhttp.Authenticator;
import com.squareup.okhttp.Credentials;
import com.squareup.okhttp.OkHttpClient;
import com.squareup.okhttp.Request;
import com.squareup.okhttp.Response;
import org.apache.commons.codec.binary.Base64;
import org.apache.commons.lang.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;
import retrofit.Endpoint;
import retrofit.RestAdapter;
import retrofit.client.OkClient;
import retrofit.converter.Converter;
import retrofit.converter.JacksonConverter;

import java.io.IOException;
import java.net.Proxy;
import java.nio.file.Files;
import java.nio.file.Paths;

import static retrofit.Endpoints.newFixedEndpoint;

@Component
public class RetrofitClientFactory {

  @Value("${retrofit.logLevel:BASIC}")
  String retrofitLogLevel;

  @Bean
  JacksonConverter jacksonConverterWithMapper(ObjectMapper objectMapper) {
    return new JacksonConverter(objectMapper);
  }

  public <T> T createClient(Class<T> type,
                            Converter converter,
                            RemoteService remoteService,
                            OkHttpClient okHttpClient) {
    try {
      return createClient(type, converter, remoteService, okHttpClient, null, null, null);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  public <T> T createClient(Class<T> type,
                            Converter converter,
                            RemoteService remoteService,
                            OkHttpClient okHttpClient,
                            String username,
                            String password,
                            String usernamePasswordFile) throws IOException {
    String baseUrl = remoteService.getBaseUrl();

    baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;

    Endpoint endpoint = newFixedEndpoint(baseUrl);

    if (!(StringUtils.isEmpty(username) && StringUtils.isEmpty(password) && StringUtils.isEmpty(usernamePasswordFile))) {
      okHttpClient = createAuthenticatedClient(username, password, usernamePasswordFile);
    }

    return new RestAdapter.Builder()
      .setEndpoint(endpoint)
      .setClient(new OkClient(okHttpClient))
      .setConverter(converter)
      .setLogLevel(RestAdapter.LogLevel.valueOf(retrofitLogLevel))
      .setLog(new Slf4jRetrofitLogger(type))
      .build()
      .create(type);
  }

  private static OkHttpClient createAuthenticatedClient(String username,
                                                        String password,
                                                        String usernamePasswordFile) throws IOException {
    final String credential;

    if (StringUtils.isNotEmpty(usernamePasswordFile)) {
      String trimmedFileContent = new String(Files.readAllBytes(Paths.get(usernamePasswordFile))).trim();

      credential = "Basic " + Base64.encodeBase64String(trimmedFileContent.getBytes());
    } else {
      credential = Credentials.basic(username, password);
    }

    OkHttpClient httpClient = new OkHttpClient();

    httpClient.setAuthenticator(new Authenticator() {
      @Override
      public Request authenticate(Proxy proxy, Response response) throws IOException {
        return response.request().newBuilder().header("Authorization", credential).build();
      }

      @Override
      public Request authenticateProxy(Proxy proxy, Response response) throws IOException {
        return response.request().newBuilder().header("Proxy-Authorization", credential).build();
      }
    });

    return httpClient;
  }
}

