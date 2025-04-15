package com.netflix.spinnaker.fiat.config;

import com.netflix.spinnaker.config.OkHttp3ClientConfiguration;
import com.netflix.spinnaker.fiat.roles.github.GitHubProperties;
import com.netflix.spinnaker.fiat.roles.github.client.GitHubClient;
import com.netflix.spinnaker.fiat.util.RetrofitUtils;
import com.netflix.spinnaker.kork.retrofit.ErrorHandlingExecutorCallAdapterFactory;
import java.io.IOException;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import okhttp3.Interceptor;
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
