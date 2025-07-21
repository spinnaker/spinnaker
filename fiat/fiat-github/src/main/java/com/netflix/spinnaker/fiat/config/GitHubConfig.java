package com.netflix.spinnaker.fiat.config;

import com.netflix.spinnaker.config.OkHttp3ClientConfiguration;
import com.netflix.spinnaker.fiat.roles.github.GitHubProperties;
import com.netflix.spinnaker.fiat.roles.github.client.GitHubAppAuthService;
import com.netflix.spinnaker.fiat.roles.github.client.GitHubAppRequestInterceptor;
import com.netflix.spinnaker.fiat.roles.github.client.GitHubClient;
import com.netflix.spinnaker.kork.retrofit.ErrorHandlingExecutorCallAdapterFactory;
import com.netflix.spinnaker.kork.retrofit.util.RetrofitUtils;
import java.io.IOException;
import javax.annotation.PostConstruct;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import okhttp3.Interceptor;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import retrofit2.Retrofit;
import retrofit2.converter.jackson.JacksonConverterFactory;

/**
 * Converts the list of GitHub Configuration properties a collection of clients to access the GitHub
 * hosts
 */
@Configuration
@ConditionalOnProperty(value = "auth.group-membership.service", havingValue = "github")
@Slf4j
public class GitHubConfig {

  @Autowired @Setter private GitHubProperties gitHubProperties;

  @PostConstruct
  public void validateConfiguration() {
    gitHubProperties.validateAuthConfiguration();

    if (gitHubProperties.shouldUseGitHubApp()) {
      log.info(
          "GitHub App authentication enabled for organization: {} (method: {})",
          gitHubProperties.getOrganization(),
          gitHubProperties.getAuthMethod());
      log.info(
          "Benefits: Better rate limits (5000/hour vs 1000/hour), enhanced security, and detailed audit logs");
    } else {
      log.info(
          "Personal Access Token authentication enabled for organization: {} (method: {})",
          gitHubProperties.getOrganization(),
          gitHubProperties.getAuthMethod());
      log.warn(
          "Consider migrating to GitHub App authentication for better rate limits (5000/hour vs 1000/hour) and enhanced security");
    }
  }

  @Bean
  public GitHubClient gitHubClient(OkHttp3ClientConfiguration okHttpClientConfig) {
    OkHttpClient.Builder clientBuilder = okHttpClientConfig.createForRetrofit2();

    Interceptor authInterceptor;
    if (gitHubProperties.shouldUseGitHubApp()) {
      GitHubAppAuthService authService =
          new GitHubAppAuthService(
              gitHubProperties.getAppId(),
              gitHubProperties.getPrivateKeyPath(),
              gitHubProperties.getInstallationId(),
              gitHubProperties.getBaseUrl(),
              clientBuilder.build());
      authInterceptor = new GitHubAppRequestInterceptor(authService);
    } else {
      authInterceptor =
          new BasicAuthRequestInterceptor().setAccessToken(gitHubProperties.getAccessToken());
    }

    return new Retrofit.Builder()
        .baseUrl(RetrofitUtils.getBaseUrl(gitHubProperties.getBaseUrl()))
        .client(clientBuilder.addInterceptor(authInterceptor).build())
        .addCallAdapterFactory(ErrorHandlingExecutorCallAdapterFactory.getInstance())
        .addConverterFactory(JacksonConverterFactory.create())
        .build()
        .create(GitHubClient.class);
  }

  @Setter
  private static class BasicAuthRequestInterceptor implements Interceptor {

    private String accessToken;

    @Override
    public @NotNull Response intercept(Chain chain) throws IOException {
      // See docs at https://developer.github.com/v3/#authentication
      Request request =
          chain.request().newBuilder().addHeader("Authorization", "token " + accessToken).build();
      return chain.proceed(request);
    }
  }
}
