/*
 * Copyright 2017 Netflix, Inc.
 * Copyright 2022 Redbox Entertainment, Inc.
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
package com.netflix.spinnaker.igor.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spinnaker.config.OkHttp3ClientConfiguration;
import com.netflix.spinnaker.igor.IgorConfigurationProperties;
import com.netflix.spinnaker.igor.gitlabci.client.GitlabCiClient;
import com.netflix.spinnaker.igor.gitlabci.service.GitlabCiService;
import com.netflix.spinnaker.igor.service.BuildServices;
import com.netflix.spinnaker.igor.util.RetrofitUtils;
import com.netflix.spinnaker.kork.retrofit.ErrorHandlingExecutorCallAdapterFactory;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Collectors;
import okhttp3.Interceptor;
import okhttp3.Request;
import okhttp3.Response;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import retrofit2.Retrofit;
import retrofit2.converter.jackson.JacksonConverterFactory;

@Configuration
@ConditionalOnProperty("gitlab-ci.enabled")
@EnableConfigurationProperties(GitlabCiProperties.class)
public class GitlabCiConfig {
  private static final Logger log = LoggerFactory.getLogger(GitlabCiConfig.class);

  @Bean
  public Map<String, GitlabCiService> masters(
      BuildServices buildServices,
      final IgorConfigurationProperties igorConfigurationProperties,
      GitlabCiProperties gitlabCiProperties,
      ObjectMapper objectMapper,
      OkHttp3ClientConfiguration okHttpClientConfig) {
    log.info("creating gitlabCiMasters");
    Map<String, GitlabCiService> gitlabCiMasters =
        gitlabCiProperties.getMasters().stream()
            .map(
                gitlabCiHost ->
                    gitlabCiService(
                        igorConfigurationProperties,
                        gitlabCiHost.getName(),
                        gitlabCiHost,
                        objectMapper,
                        okHttpClientConfig))
            .collect(Collectors.toMap(GitlabCiService::getName, Function.identity()));
    buildServices.addServices(gitlabCiMasters);
    return gitlabCiMasters;
  }

  private static GitlabCiService gitlabCiService(
      IgorConfigurationProperties igorConfigurationProperties,
      String name,
      GitlabCiProperties.GitlabCiHost host,
      ObjectMapper objectMapper,
      OkHttp3ClientConfiguration okHttpClientConfig) {
    return new GitlabCiService(
        gitlabCiClient(
            host.getAddress(),
            host.getPrivateToken(),
            igorConfigurationProperties.getClient().getTimeout(),
            objectMapper,
            okHttpClientConfig),
        name,
        host,
        host.getPermissions().build());
  }

  public static GitlabCiClient gitlabCiClient(
      String address,
      String privateToken,
      int timeout,
      ObjectMapper objectMapper,
      OkHttp3ClientConfiguration okHttpClientConfig) {

    return new Retrofit.Builder()
        .baseUrl(RetrofitUtils.getBaseUrl(address))
        .client(
            okHttpClientConfig
                .createForRetrofit2()
                .readTimeout(timeout, TimeUnit.MILLISECONDS)
                .addInterceptor(new GitlabCiHeaders(privateToken))
                .build())
        .addConverterFactory(JacksonConverterFactory.create(objectMapper))
        .addCallAdapterFactory(ErrorHandlingExecutorCallAdapterFactory.getInstance())
        .build()
        .create(GitlabCiClient.class);
  }

  public static class GitlabCiHeaders implements Interceptor {
    GitlabCiHeaders(String privateToken) {
      this.privateToken = privateToken;
    }

    @Override
    public Response intercept(Chain chain) throws IOException {
      Request.Builder builder = chain.request().newBuilder();
      if (!StringUtils.isEmpty(privateToken)) {
        builder.addHeader("PRIVATE-TOKEN", privateToken);
      }
      return chain.proceed(builder.build());
    }

    private String privateToken;
  }
}
