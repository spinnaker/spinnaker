package com.netflix.spinnaker.fiat.config;

import com.jakewharton.retrofit.Ok3Client;
import com.netflix.spinnaker.config.DefaultServiceEndpoint;
import com.netflix.spinnaker.config.okhttp3.OkHttpClientProvider;
import com.netflix.spinnaker.fiat.roles.github.GitHubProperties;
import com.netflix.spinnaker.fiat.roles.github.client.GitHubClient;
import com.netflix.spinnaker.kork.retrofit.exceptions.SpinnakerRetrofitErrorHandler;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import retrofit.Endpoints;
import retrofit.RequestInterceptor;
import retrofit.RestAdapter;
import retrofit.converter.JacksonConverter;

/**
 * Converts the list of GitHub Configuration properties a collection of clients to access the GitHub
 * hosts
 */
@Configuration
@ConditionalOnProperty(value = "auth.group-membership.service", havingValue = "github")
@Slf4j
public class GitHubConfig {

  @Autowired @Setter private RestAdapter.LogLevel retrofitLogLevel;

  @Autowired @Setter private GitHubProperties gitHubProperties;

  @Bean
  public GitHubClient gitHubClient(OkHttpClientProvider clientProvider) {
    BasicAuthRequestInterceptor interceptor =
        new BasicAuthRequestInterceptor().setAccessToken(gitHubProperties.getAccessToken());

    return new RestAdapter.Builder()
        .setEndpoint(Endpoints.newFixedEndpoint(gitHubProperties.getBaseUrl()))
        .setRequestInterceptor(interceptor)
        .setClient(
            new Ok3Client(
                clientProvider.getClient(
                    new DefaultServiceEndpoint("github", gitHubProperties.getBaseUrl()))))
        .setConverter(new JacksonConverter())
        .setErrorHandler(SpinnakerRetrofitErrorHandler.getInstance())
        .setLogLevel(retrofitLogLevel)
        .setLog(new Slf4jRetrofitLogger(GitHubClient.class))
        .build()
        .create(GitHubClient.class);
  }

  private static class Slf4jRetrofitLogger implements RestAdapter.Log {
    private final Logger logger;

    Slf4jRetrofitLogger(Class type) {
      this(LoggerFactory.getLogger(type));
    }

    Slf4jRetrofitLogger(Logger logger) {
      this.logger = logger;
    }

    @Override
    public void log(String message) {
      logger.info(message);
    }
  }

  private static class BasicAuthRequestInterceptor implements RequestInterceptor {

    @Setter private String accessToken;

    @Override
    public void intercept(RequestFacade request) {
      // See docs at https://developer.github.com/v3/#authentication
      request.addHeader("Authorization", "token " + accessToken);
    }
  }
}
