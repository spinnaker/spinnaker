package com.netflix.spinnaker.fiat.config;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableList;
import com.netflix.spinnaker.config.OkHttpClientConfiguration;
import com.netflix.spinnaker.fiat.permissions.InMemoryPermissionsRepository;
import com.netflix.spinnaker.fiat.permissions.PermissionsRepository;
import com.netflix.spinnaker.fiat.roles.UserRolesProvider;
import com.squareup.okhttp.ConnectionPool;
import com.squareup.okhttp.Interceptor;
import com.squareup.okhttp.Request;
import com.squareup.okhttp.Response;
import lombok.AllArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Scope;
import org.springframework.util.backoff.BackOffExecution;
import org.springframework.util.backoff.ExponentialBackOff;
import retrofit.RestAdapter;
import retrofit.client.OkClient;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.TimeUnit;

@Configuration
public class FiatConfig {

  @Autowired
  @Setter
  private OkHttpClientConfiguration okHttpClientConfig;

  @Value("${okHttpClient.connectionPool.maxIdleConnections:5}")
  @Setter
  private int maxIdleConnections;

  @Value("${okHttpClient.connectionPool.keepAliveDurationMs:300000}")
  @Setter
  private int keepAliveDurationMs;

  @Value("${okHttpClient.retryOnConnectionFailure:true}")
  @Setter
  private boolean retryOnConnectionFailure;

  @Value("${okHttpClient.retries.maxElapsedBackoffMs:5000}")
  @Setter
  private long maxElapsedBackoffMs;

  @Bean
  @ConditionalOnMissingBean(PermissionsRepository.class)
  PermissionsRepository permissionsRepository() {
    return new InMemoryPermissionsRepository();
  }

  @Bean
  @Primary
  ObjectMapper objectMapper() {
    return new ObjectMapper()
        .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
        .setSerializationInclusion(JsonInclude.Include.NON_NULL);
  }

  @Bean
  @ConditionalOnMissingBean(UserRolesProvider.class)
  UserRolesProvider defaultUserRolesProvider() {
    return new UserRolesProvider() {
      @Override
      public Map<String, Collection<String>> multiLoadRoles(Collection<String> userIds) {
        return new HashMap<>();
      }

      @Override
      public List<String> loadRoles(String userId) {
        return new ArrayList<>();
      }
    };
  }

  @Bean
  RestAdapter.LogLevel retrofitLogLevel(@Value("${retrofit.logLevel:NONE}") String logLevel) {
    return RestAdapter.LogLevel.valueOf(logLevel);
  }

  @Bean
  @Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
  OkClient okClient() {
    val client = okHttpClientConfig.create();
    client.setConnectionPool(new ConnectionPool(maxIdleConnections, keepAliveDurationMs));
    client.setRetryOnConnectionFailure(retryOnConnectionFailure);
    client.interceptors().add(new RetryingInterceptor(maxElapsedBackoffMs));
    return new OkClient(client);
  }

  @Slf4j
  @AllArgsConstructor
  private static class RetryingInterceptor implements Interceptor {

    // http://restcookbook.com/HTTP%20Methods/idempotency/
    private static final List<String> NON_RETRYABLE_METHODS = ImmutableList.of("POST", "PATCH");

    private long maxElapsedBackoffMs;

    @Override
    public Response intercept(Chain chain) throws IOException {
      ExponentialBackOff backoff = new ExponentialBackOff();
      backoff.setMaxElapsedTime(maxElapsedBackoffMs);
      BackOffExecution backOffExec = backoff.start();

      Response response = null;
      long waitTime = 0;
      while (waitTime != BackOffExecution.STOP) {
        Request request = chain.request();
        response = chain.proceed(request);
        if (response.isSuccessful() || NON_RETRYABLE_METHODS.contains(request.method())) {
          return response;
        }

        try {
          waitTime = backOffExec.nextBackOff();
          if (waitTime != BackOffExecution.STOP) {
            response.body().close();
            log.warn("Request for " + request.urlString() + " failed. Backing off for " + waitTime + "ms");
            Thread.sleep(waitTime);
          }
        } catch (Throwable ignored) {
          break;
        }
      }
      return response;
    }
  }
}
