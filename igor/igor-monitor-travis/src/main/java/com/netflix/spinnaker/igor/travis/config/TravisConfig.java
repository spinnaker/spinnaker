/*
 * Copyright 2018 Schibsted ASA.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 *
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

package com.netflix.spinnaker.igor.travis.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spinnaker.config.OkHttp3ClientConfiguration;
import com.netflix.spinnaker.igor.IgorConfigurationProperties;
import com.netflix.spinnaker.igor.service.ArtifactDecorator;
import com.netflix.spinnaker.igor.service.BuildServices;
import com.netflix.spinnaker.igor.travis.TravisCache;
import com.netflix.spinnaker.igor.travis.client.TravisClient;
import com.netflix.spinnaker.igor.travis.client.model.v3.Root;
import com.netflix.spinnaker.igor.travis.service.TravisService;
import com.netflix.spinnaker.igor.util.RetrofitUtils;
import com.netflix.spinnaker.kork.retrofit.ErrorHandlingExecutorCallAdapterFactory;
import com.netflix.spinnaker.kork.retrofit.Retrofit2SyncCall;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Collectors;
import javax.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import okhttp3.Interceptor;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import retrofit2.Retrofit;
import retrofit2.converter.jackson.JacksonConverterFactory;

/**
 * Converts the list of Travis Configuration properties a collection of clients to access the Travis
 * hosts
 */
@Configuration
@ConditionalOnProperty("travis.enabled")
@EnableConfigurationProperties(TravisProperties.class)
@Slf4j
public class TravisConfig {

  @Bean
  public Map<String, TravisService> travisMasters(
      BuildServices buildServices,
      TravisCache travisCache,
      IgorConfigurationProperties igorConfigurationProperties,
      @Valid TravisProperties travisProperties,
      ObjectMapper objectMapper,
      Optional<ArtifactDecorator> artifactDecorator,
      CircuitBreakerRegistry circuitBreakerRegistry,
      OkHttp3ClientConfiguration okHttp3ClientConfig) {
    log.info("creating travisMasters");

    Map<String, TravisService> travisMasters =
        (travisProperties == null
                ? new ArrayList<TravisProperties.TravisHost>()
                : travisProperties.getMasters())
            .stream()
                .map(
                    host -> {
                      String travisName = "travis-" + host.getName();
                      log.info("bootstrapping {} as {}", host.getAddress(), travisName);

                      TravisClient client =
                          travisClient(
                              host.getAddress(),
                              igorConfigurationProperties.getClient().getTimeout(),
                              objectMapper,
                              okHttp3ClientConfig);

                      boolean useLegacyLogFetching = true;
                      if (host.isUseLogComplete()) {
                        try {
                          Root root = Retrofit2SyncCall.execute(client.getRoot());
                          useLegacyLogFetching = !root.hasLogCompleteAttribute();
                          if (useLegacyLogFetching) {
                            log.info(
                                "It seems Travis Enterprise is older than version 2.2.9. Will use legacy log fetching.");
                          }
                        } catch (Exception e) {
                          log.warn(
                              "Could not query Travis API to check API compatibility for log_complete. "
                                  + "Will use legacy log fetching.",
                              e);
                        }
                      }

                      return new TravisService(
                          travisName,
                          host.getBaseUrl(),
                          host.getGithubToken(),
                          host.getNumberOfJobs(),
                          host.getBuildResultLimit(),
                          host.getFilteredRepositories(),
                          client,
                          travisCache,
                          artifactDecorator,
                          travisProperties.getRegexes(),
                          travisProperties.getBuildMessageKey(),
                          host.getPermissions().build(),
                          useLegacyLogFetching,
                          circuitBreakerRegistry);
                    })
                .collect(Collectors.toMap(TravisService::getGroupKey, Function.identity()));

    buildServices.addServices(travisMasters);
    return travisMasters;
  }

  public static TravisClient travisClient(
      String address,
      int timeout,
      ObjectMapper objectMapper,
      OkHttp3ClientConfiguration okHttpClientConfig) {
    OkHttpClient client =
        okHttpClientConfig
            .createForRetrofit2()
            .readTimeout(timeout, TimeUnit.MILLISECONDS)
            .addInterceptor(new TravisHeader())
            .build();

    return new Retrofit.Builder()
        .baseUrl(RetrofitUtils.getBaseUrl(address))
        .client(client)
        .addConverterFactory(JacksonConverterFactory.create(objectMapper))
        .addCallAdapterFactory(ErrorHandlingExecutorCallAdapterFactory.getInstance())
        .build()
        .create(TravisClient.class);
  }

  public static class TravisHeader implements Interceptor {
    public okhttp3.Response intercept(Chain chain) throws IOException {
      Request request =
          chain
              .request()
              .newBuilder()
              .addHeader("Accept", "application/vnd.travis-ci.2+json")
              .addHeader("User-Agent", "Travis-Igor")
              .addHeader("Content-Type", "application/json")
              .build();
      return chain.proceed(request);
    }
  }
}
