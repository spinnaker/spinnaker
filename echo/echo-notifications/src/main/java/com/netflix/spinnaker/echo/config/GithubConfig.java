/*
 * Copyright 2018 Schibsted ASA
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

package com.netflix.spinnaker.echo.config;

import com.netflix.spinnaker.config.OkHttp3ClientConfiguration;
import com.netflix.spinnaker.echo.github.GithubAppTokenProvider;
import com.netflix.spinnaker.echo.github.GithubService;
import com.netflix.spinnaker.echo.github.GithubTokenProvider;
import com.netflix.spinnaker.kork.github.GitHubAppCredentials;
import com.netflix.spinnaker.kork.retrofit.ErrorHandlingExecutorCallAdapterFactory;
import com.netflix.spinnaker.kork.retrofit.util.RetrofitUtils;
import java.io.IOException;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.util.StringUtils;
import retrofit2.Retrofit;
import retrofit2.converter.jackson.JacksonConverterFactory;

@Configuration
@ConditionalOnProperty("github-status.enabled")
@EnableConfigurationProperties(GithubStatusProperties.class)
@Slf4j
public class GithubConfig {

  private static final String API_BASE_URL_PROPERTY = "github-status.github-app.api-base-url";

  private final GithubStatusProperties properties;

  public GithubConfig(GithubStatusProperties properties) {
    this.properties = properties;
  }

  @Bean
  public GithubService githubService(OkHttp3ClientConfiguration okHttpClientConfig) {
    log.info("Github service loaded");

    return new Retrofit.Builder()
        .baseUrl(RetrofitUtils.getBaseUrl(properties.getEndpoint()))
        .client(okHttpClientConfig.createForRetrofit2().build())
        .addCallAdapterFactory(ErrorHandlingExecutorCallAdapterFactory.getInstance())
        .addConverterFactory(JacksonConverterFactory.create())
        .build()
        .create(GithubService.class);
  }

  /**
   * Authenticates GitHub status requests with exactly one of {@code github-status.token} or {@code
   * github-status.github-app}.
   */
  @Bean
  public GithubTokenProvider githubTokenProvider(Environment environment) throws IOException {
    boolean hasToken = StringUtils.hasText(properties.getToken());
    GitHubAppCredentials githubApp = properties.getGithubApp();
    if (hasToken && githubApp != null) {
      throw new IllegalStateException(
          "Both github-status.token and github-status.github-app are configured; configure"
              + " exactly one.");
    }
    if (!hasToken && githubApp == null) {
      throw new IllegalStateException(
          "github-status.enabled is true but neither github-status.token nor"
              + " github-status.github-app is configured; configure exactly one.");
    }
    if (hasToken) {
      log.info("Github status notifications authenticate with a personal access token");
      return GithubTokenProvider.personalAccessToken(properties.getToken());
    }

    // The App talks to the same GitHub as the status requests unless told otherwise
    if (!Binder.get(environment).bind(API_BASE_URL_PROPERTY, String.class).isBound()) {
      githubApp =
          new GitHubAppCredentials(
              githubApp.getAppId(),
              githubApp.getAppPrivateKeyPath(),
              githubApp.getAppInstallationId(),
              properties.getEndpoint(),
              List.copyOf(githubApp.getAllowedOrganizations()));
    }
    log.info(
        "Github status notifications authenticate as GitHub App {} (installation: {})",
        githubApp.getAppId(),
        githubApp.hasInstallationId()
            ? githubApp.getAppInstallationId()
            : "derived from the repository");
    return new GithubAppTokenProvider(
        githubApp.toAuthenticator("github-status notifications"), githubApp.hasInstallationId());
  }
}
