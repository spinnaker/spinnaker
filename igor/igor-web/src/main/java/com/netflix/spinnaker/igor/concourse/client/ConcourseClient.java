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
import com.fasterxml.jackson.databind.PropertyNamingStrategy;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.jakewharton.retrofit.Ok3Client;
import com.netflix.spinnaker.igor.concourse.client.model.ClusterInfo;
import com.netflix.spinnaker.igor.concourse.client.model.Token;
import com.netflix.spinnaker.kork.retrofit.exceptions.SpinnakerRetrofitErrorHandler;
import com.netflix.spinnaker.retrofit.Slf4jRetrofitLogger;
import com.vdurmont.semver4j.Semver;
import java.time.ZonedDateTime;
import lombok.Getter;
import okhttp3.OkHttpClient;
import retrofit.RequestInterceptor;
import retrofit.RestAdapter;
import retrofit.client.Response;
import retrofit.converter.JacksonConverter;

public class ConcourseClient {
  private final String host;
  private final String user;
  private final String password;
  private final OkHttpClient okHttpClient;

  private final SkyService skyServiceV1;
  private final SkyServiceV2 skyServiceV2;
  private final TokenService tokenServiceV1;
  private final TokenServiceV2 tokenServiceV2;
  private final TokenServiceV3 tokenServiceV3;

  @Getter private ClusterInfoService clusterInfoService;

  @Getter private BuildService buildService;

  @Getter private JobService jobService;

  @Getter private EventService eventService;

  @Getter private TeamService teamService;

  @Getter private PipelineService pipelineService;

  @Getter private ResourceService resourceService;

  private volatile ZonedDateTime tokenExpiration = ZonedDateTime.now().minusSeconds(1);
  private volatile Token token;

  private JacksonConverter jacksonConverter;

  private final RequestInterceptor oauthInterceptor =
      new RequestInterceptor() {
        @Override
        public void intercept(RequestFacade request) {
          refreshTokenIfNecessary();
          request.addHeader("Authorization", "bearer " + token.getAccessToken());
        }
      };

  public ConcourseClient(String host, String user, String password) {
    this.host = host;
    this.user = user;
    this.password = password;

    ObjectMapper mapper =
        new ObjectMapper()
            .setPropertyNamingStrategy(PropertyNamingStrategy.SNAKE_CASE)
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .registerModule(new JavaTimeModule());

    this.okHttpClient = OkHttpClientBuilder.retryingClient3(this::refreshToken);
    this.jacksonConverter = new JacksonConverter(mapper);

    RestAdapter.Builder tokenRestBuilder =
        new RestAdapter.Builder()
            .setEndpoint(host)
            .setClient(new Ok3Client(okHttpClient))
            .setConverter(jacksonConverter)
            .setErrorHandler(SpinnakerRetrofitErrorHandler.getInstance())
            .setRequestInterceptor(
                request -> {
                  request.addHeader("Authorization", "Basic Zmx5OlpteDU=");
                });

    this.clusterInfoService =
        new RestAdapter.Builder()
            .setEndpoint(host)
            .setClient(new Ok3Client(okHttpClient))
            .setConverter(jacksonConverter)
            .setLog(new Slf4jRetrofitLogger(ClusterInfoService.class))
            .setErrorHandler(SpinnakerRetrofitErrorHandler.getInstance())
            .build()
            .create(ClusterInfoService.class);

    ClusterInfo clusterInfo = this.clusterInfoService.clusterInfo();

    Semver clusterVer = new Semver(clusterInfo.getVersion());

    if (clusterVer.isLowerThan("6.1.0")) {
      this.tokenServiceV1 =
          tokenRestBuilder
              .setLog(new Slf4jRetrofitLogger(TokenService.class))
              .setErrorHandler(SpinnakerRetrofitErrorHandler.getInstance())
              .build()
              .create(TokenService.class);
      this.tokenServiceV2 = null;
      this.tokenServiceV3 = null;

      this.skyServiceV1 = createService(SkyService.class);
      this.skyServiceV2 = null;

    } else if (clusterVer.isLowerThan("6.5.0")) {
      this.tokenServiceV1 = null;
      this.tokenServiceV2 =
          tokenRestBuilder
              .setLog(new Slf4jRetrofitLogger(TokenServiceV2.class))
              .setErrorHandler(SpinnakerRetrofitErrorHandler.getInstance())
              .build()
              .create(TokenServiceV2.class);
      this.tokenServiceV3 = null;

      this.skyServiceV1 = null;
      this.skyServiceV2 = createService(SkyServiceV2.class);

    } else {
      this.tokenServiceV1 = null;
      this.tokenServiceV2 = null;
      this.tokenServiceV3 =
          tokenRestBuilder
              .setLog(new Slf4jRetrofitLogger(TokenServiceV3.class))
              .setErrorHandler(SpinnakerRetrofitErrorHandler.getInstance())
              .build()
              .create(TokenServiceV3.class);

      this.skyServiceV1 = null;
      this.skyServiceV2 = createService(SkyServiceV2.class);
    }

    this.buildService = createService(BuildService.class);
    this.jobService = createService(JobService.class);
    this.teamService = createService(TeamService.class);
    this.pipelineService = createService(PipelineService.class);
    this.resourceService = createService(ResourceService.class);
    this.eventService = new EventService(host, this::refreshToken, mapper);
  }

  private void refreshTokenIfNecessary() {
    if (tokenExpiration.isBefore(ZonedDateTime.now())) {
      this.refreshToken();
    }
  }

  private Token refreshToken() {
    if (tokenServiceV1 != null) {
      token =
          tokenServiceV1.passwordToken(
              "password", user, password, "openid profile email federated:id groups");

    } else if (tokenServiceV2 != null) {
      token =
          tokenServiceV2.passwordToken(
              "password", user, password, "openid profile email federated:id groups");

    } else if (tokenServiceV3 != null) {
      token =
          tokenServiceV3.passwordToken(
              "password", user, password, "openid profile email federated:id groups");
    }

    tokenExpiration = token.getExpiry();
    return token;
  }

  public Response userInfo() {
    return skyServiceV1 != null ? skyServiceV1.userInfo() : skyServiceV2.userInfo();
  }

  private <S> S createService(Class<S> serviceClass) {
    return new RestAdapter.Builder()
        .setEndpoint(host)
        .setClient(new Ok3Client(okHttpClient))
        .setConverter(jacksonConverter)
        .setRequestInterceptor(oauthInterceptor)
        .setLog(new Slf4jRetrofitLogger(serviceClass))
        .setErrorHandler(SpinnakerRetrofitErrorHandler.getInstance())
        .build()
        .create(serviceClass);
  }
}
