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

import com.netflix.spinnaker.config.OkHttp3ClientConfiguration;
import com.netflix.spinnaker.kork.retrofit.ErrorHandlingExecutorCallAdapterFactory;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import okhttp3.Credentials;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import org.apache.commons.lang3.StringUtils;
import org.apache.tomcat.util.codec.binary.Base64;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import retrofit2.Converter.Factory;
import retrofit2.Retrofit;

@Component
public class RetrofitClientFactory {

  @Autowired OkHttp3ClientConfiguration okHttp3ClientConfig;

  public <T> T createClient(Class<T> type, Factory converterFactory, RemoteService remoteService) {
    return createClient(
        type, converterFactory, remoteService, okHttp3ClientConfig.createForRetrofit2().build());
  }

  public <T> T createClient(
      Class<T> type,
      Factory converterFactory,
      RemoteService remoteService,
      OkHttpClient okHttpClient) {
    return createClient(type, converterFactory, remoteService.getBaseUrl(), okHttpClient);
  }

  public <T> T createClient(
      Class<T> type,
      Factory converterFactory,
      RemoteService remoteService,
      String username,
      String password,
      String usernamePasswordFile,
      String bearerToken)
      throws IOException {
    OkHttpClient okHttpClient;
    if (!(StringUtils.isEmpty(username)
        && StringUtils.isEmpty(password)
        && StringUtils.isEmpty(usernamePasswordFile)
        && StringUtils.isEmpty(bearerToken))) {
      okHttpClient =
          createAuthenticatedClient(
              okHttp3ClientConfig.createForRetrofit2(),
              username,
              password,
              usernamePasswordFile,
              bearerToken);
    } else {
      okHttpClient = okHttp3ClientConfig.createForRetrofit2().build();
    }
    return createClient(type, converterFactory, remoteService, okHttpClient);
  }

  private <T> T createClient(
      Class<T> type, Factory converterFactory, String baseUrl, OkHttpClient okHttpClient) {

    return new Retrofit.Builder()
        .baseUrl(getBaseUrl(baseUrl))
        .client(okHttpClient)
        .addCallAdapterFactory(ErrorHandlingExecutorCallAdapterFactory.getInstance())
        .addConverterFactory(converterFactory)
        .build()
        .create(type);
  }

  private static OkHttpClient createAuthenticatedClient(
      OkHttpClient.Builder okHttp3ClientBuilder,
      String username,
      String password,
      String usernamePasswordFile,
      String bearerToken)
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

    return okHttp3ClientBuilder
        .authenticator(
            (route, response) ->
                response.request().newBuilder().header("Authorization", credential).build())
        .build();
  }

  public static String getBaseUrl(String suppliedBaseUrl) {
    HttpUrl parsedUrl = HttpUrl.parse(suppliedBaseUrl);
    if (parsedUrl == null) {
      throw new IllegalArgumentException("Invalid URL: " + suppliedBaseUrl);
    }
    String baseUrl = parsedUrl.newBuilder().build().toString();
    if (!baseUrl.endsWith("/")) {
      baseUrl += "/";
    }
    return baseUrl;
  }
}
