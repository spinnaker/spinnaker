package com.netflix.spinnaker.fiat.config;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.netflix.spinnaker.config.OkHttpClientConfiguration;
import com.netflix.spinnaker.fiat.permissions.InMemoryPermissionsRepository;
import com.netflix.spinnaker.fiat.permissions.PermissionsRepository;
import com.netflix.spinnaker.fiat.roles.UserRolesProvider;
import com.squareup.okhttp.ConnectionPool;
import lombok.Setter;
import lombok.val;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Scope;
import retrofit.RestAdapter;
import retrofit.client.OkClient;

import java.util.*;

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
    return new OkClient(client);
  }
}
