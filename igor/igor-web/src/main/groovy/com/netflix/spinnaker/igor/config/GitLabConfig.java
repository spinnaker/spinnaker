/*
 * Copyright 2017 bol.com
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

import com.netflix.spinnaker.config.OkHttp3ClientConfiguration;
import com.netflix.spinnaker.igor.scm.gitlab.client.GitLabClient;
import com.netflix.spinnaker.igor.scm.gitlab.client.GitLabMaster;
import com.netflix.spinnaker.igor.util.RetrofitUtils;
import com.netflix.spinnaker.kork.retrofit.ErrorHandlingExecutorCallAdapterFactory;
import java.io.IOException;
import javax.validation.Valid;
import okhttp3.Interceptor;
import okhttp3.Request;
import okhttp3.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import retrofit2.Retrofit;
import retrofit2.converter.jackson.JacksonConverterFactory;

@Configuration
@ConditionalOnProperty("gitlab.base-url")
@EnableConfigurationProperties(GitLabProperties.class)
public class GitLabConfig {
  private static final Logger log = LoggerFactory.getLogger(GitLabConfig.class);

  @Bean
  public GitLabMaster gitLabMasters(
      @Valid GitLabProperties gitLabProperties, OkHttp3ClientConfiguration okHttpClientConfig) {
    log.info("bootstrapping {} as gitlab", gitLabProperties.getBaseUrl());
    return new GitLabMaster(
        gitLabClient(
            gitLabProperties.getBaseUrl(), gitLabProperties.getPrivateToken(), okHttpClientConfig),
        gitLabProperties.getBaseUrl());
  }

  public GitLabClient gitLabClient(
      String address, String privateToken, OkHttp3ClientConfiguration okHttpClientConfig) {
    return new Retrofit.Builder()
        .baseUrl(RetrofitUtils.getBaseUrl(address))
        .client(
            okHttpClientConfig
                .createForRetrofit2()
                .addInterceptor(new PrivateTokenRequestInterceptor(privateToken))
                .build())
        .addConverterFactory(JacksonConverterFactory.create())
        .addCallAdapterFactory(ErrorHandlingExecutorCallAdapterFactory.getInstance())
        .build()
        .create(GitLabClient.class);
  }

  static class PrivateTokenRequestInterceptor implements Interceptor {
    private final String privateToken;

    PrivateTokenRequestInterceptor(String privateToken) {
      this.privateToken = privateToken;
    }

    @Override
    public Response intercept(Chain chain) throws IOException {
      Request request =
          chain.request().newBuilder().addHeader("Private-Token", privateToken).build();
      return chain.proceed(request);
    }
  }
}
