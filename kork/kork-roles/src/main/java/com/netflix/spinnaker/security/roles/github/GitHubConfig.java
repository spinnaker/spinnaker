/*
 * Copyright 2026 DoorDash, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.security.roles.github;

import com.netflix.spinnaker.config.OkHttp3ClientConfiguration;
import com.netflix.spinnaker.kork.retrofit.ErrorHandlingExecutorCallAdapterFactory;
import com.netflix.spinnaker.kork.retrofit.util.RetrofitUtils;
import com.netflix.spinnaker.security.roles.github.client.GitHubClient;
import java.io.IOException;
import javax.annotation.Nonnull;
import lombok.Setter;
import lombok.experimental.Accessors;
import lombok.extern.slf4j.Slf4j;
import okhttp3.Interceptor;
import okhttp3.Request;
import okhttp3.Response;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import retrofit2.Retrofit;
import retrofit2.converter.jackson.JacksonConverterFactory;

/** Builds the Retrofit {@link GitHubClient} used to access the GitHub host. */
@Configuration
@ConditionalOnProperty(value = "auth.group-membership.service", havingValue = "github")
@Slf4j
public class GitHubConfig {

  @Autowired @Setter private GitHubProperties gitHubProperties;

  @Bean
  public GitHubClient gitHubClient(OkHttp3ClientConfiguration okHttpClientConfig) {
    BasicAuthRequestInterceptor interceptor =
        new BasicAuthRequestInterceptor().setAccessToken(gitHubProperties.getAccessToken());

    return new Retrofit.Builder()
        .baseUrl(RetrofitUtils.getBaseUrl(gitHubProperties.getBaseUrl()))
        .client(okHttpClientConfig.createForRetrofit2().addInterceptor(interceptor).build())
        .addCallAdapterFactory(ErrorHandlingExecutorCallAdapterFactory.getInstance())
        .addConverterFactory(JacksonConverterFactory.create())
        .build()
        .create(GitHubClient.class);
  }

  @Setter
  @Accessors(chain = true)
  private static class BasicAuthRequestInterceptor implements Interceptor {

    private String accessToken;

    @Override
    public @Nonnull Response intercept(Chain chain) throws IOException {
      // See docs at https://developer.github.com/v3/#authentication
      Request request =
          chain.request().newBuilder().addHeader("Authorization", "token " + accessToken).build();
      return chain.proceed(request);
    }
  }
}
