/*
 * Copyright 2019 Pivotal, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.igor.concourse.client;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.netflix.spinnaker.config.OkHttp3ClientConfiguration;
import com.netflix.spinnaker.igor.concourse.client.model.ClusterInfo;
import com.netflix.spinnaker.igor.concourse.client.model.Token;
import com.netflix.spinnaker.igor.util.RetrofitUtils;
import com.netflix.spinnaker.kork.retrofit.ErrorHandlingExecutorCallAdapterFactory;
import com.netflix.spinnaker.kork.retrofit.Retrofit2SyncCall;
import com.vdurmont.semver4j.Semver;
import java.io.IOException;
import java.time.ZonedDateTime;
import lombok.Getter;
import okhttp3.Interceptor;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.ResponseBody;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.jackson.JacksonConverterFactory;

public class ConcourseClient {
  private final String host;
  private final String user;
  private final String password;

  private final OkHttp3ClientConfiguration okHttpClientConfig;

  private final SkyService skyServiceV1;
  private final SkyServiceV2 skyServiceV2;
  private final TokenService tokenServiceV1;
  private final TokenServiceV2 tokenServiceV2;
  private final TokenServiceV3 tokenServiceV3;
  private final JacksonConverterFactory jacksonConverterFactory;

  @Getter private ClusterInfoService clusterInfoService;

  @Getter private BuildService buildService;

  @Getter private JobService jobService;

  @Getter private EventService eventService;

  @Getter private TeamService teamService;

  @Getter private PipelineService pipelineService;

  @Getter private ResourceService resourceService;

  private volatile ZonedDateTime tokenExpiration = ZonedDateTime.now().minusSeconds(1);
  private volatile Token token;

  private final Interceptor oauthInterceptor =
      new Interceptor() {
        @Override
        public okhttp3.Response intercept(Chain chain) throws IOException {
          refreshTokenIfNecessary();
          Request request =
              chain
                  .request()
                  .newBuilder()
                  .addHeader("Authorization", "bearer " + token.getAccessToken())
                  .build();
          return chain.proceed(request);
        }
      };

  public ConcourseClient(
      String host, String user, String password, OkHttp3ClientConfiguration okHttpClientConfig) {
    this.host = host;
    this.user = user;
    this.password = password;
    this.okHttpClientConfig = okHttpClientConfig;

    ObjectMapper mapper =
        new ObjectMapper()
            .setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .registerModule(new JavaTimeModule());

    OkHttpClient tokenClient =
        OkHttpClientBuilder.retryingClient3(okHttpClientConfig, this::refreshToken)
            .addInterceptor(
                chain -> {
                  Request request =
                      chain
                          .request()
                          .newBuilder()
                          .addHeader("Authorization", "Basic Zmx5OlpteDU=")
                          .build();
                  return chain.proceed(request);
                })
            .build();

    this.jacksonConverterFactory = JacksonConverterFactory.create(mapper);

    Retrofit.Builder tokenRestBuilder =
        new Retrofit.Builder()
            .baseUrl(RetrofitUtils.getBaseUrl(host))
            .client(tokenClient)
            .addConverterFactory(jacksonConverterFactory)
            .addCallAdapterFactory(ErrorHandlingExecutorCallAdapterFactory.getInstance());

    this.clusterInfoService =
        new Retrofit.Builder()
            .baseUrl(RetrofitUtils.getBaseUrl(host))
            .client(
                OkHttpClientBuilder.retryingClient3(okHttpClientConfig, this::refreshToken).build())
            .addConverterFactory(jacksonConverterFactory)
            .addCallAdapterFactory(ErrorHandlingExecutorCallAdapterFactory.getInstance())
            .build()
            .create(ClusterInfoService.class);

    ClusterInfo clusterInfo = Retrofit2SyncCall.execute(this.clusterInfoService.clusterInfo());

    Semver clusterVer = new Semver(clusterInfo.getVersion());

    if (clusterVer.isLowerThan("6.1.0")) {
      this.tokenServiceV1 = tokenRestBuilder.build().create(TokenService.class);
      this.tokenServiceV2 = null;
      this.tokenServiceV3 = null;

      this.skyServiceV1 = createService(SkyService.class);
      this.skyServiceV2 = null;

    } else if (clusterVer.isLowerThan("6.5.0")) {
      this.tokenServiceV1 = null;
      this.tokenServiceV2 = tokenRestBuilder.build().create(TokenServiceV2.class);
      this.tokenServiceV3 = null;

      this.skyServiceV1 = null;
      this.skyServiceV2 = createService(SkyServiceV2.class);

    } else {
      this.tokenServiceV1 = null;
      this.tokenServiceV2 = null;
      this.tokenServiceV3 = tokenRestBuilder.build().create(TokenServiceV3.class);

      this.skyServiceV1 = null;
      this.skyServiceV2 = createService(SkyServiceV2.class);
    }

    this.buildService = createService(BuildService.class);
    this.jobService = createService(JobService.class);
    this.teamService = createService(TeamService.class);
    this.pipelineService = createService(PipelineService.class);
    this.resourceService = createService(ResourceService.class);
    this.eventService = new EventService(host, this::refreshToken, mapper, okHttpClientConfig);
  }

  private void refreshTokenIfNecessary() {
    if (tokenExpiration.isBefore(ZonedDateTime.now())) {
      this.refreshToken();
    }
  }

  private Token refreshToken() {
    if (tokenServiceV1 != null) {
      token =
          Retrofit2SyncCall.execute(
              tokenServiceV1.passwordToken(
                  "password", user, password, "openid profile email federated:id groups"));

    } else if (tokenServiceV2 != null) {
      token =
          Retrofit2SyncCall.execute(
              tokenServiceV2.passwordToken(
                  "password", user, password, "openid profile email federated:id groups"));

    } else if (tokenServiceV3 != null) {
      token =
          Retrofit2SyncCall.execute(
              tokenServiceV3.passwordToken(
                  "password", user, password, "openid profile email federated:id groups"));
    }

    tokenExpiration = token.getExpiry();
    return token;
  }

  public Response<ResponseBody> userInfo() {
    return Retrofit2SyncCall.executeCall(
        skyServiceV1 != null ? skyServiceV1.userInfo() : skyServiceV2.userInfo());
  }

  private <S> S createService(Class<S> serviceClass) {
    OkHttpClient okHttpClient =
        OkHttpClientBuilder.retryingClient3(okHttpClientConfig, this::refreshToken)
            .addInterceptor(oauthInterceptor)
            .build();

    return new Retrofit.Builder()
        .baseUrl(RetrofitUtils.getBaseUrl(host))
        .client(okHttpClient)
        .addConverterFactory(jacksonConverterFactory)
        .addCallAdapterFactory(ErrorHandlingExecutorCallAdapterFactory.getInstance())
        .build()
        .create(serviceClass);
  }
}
