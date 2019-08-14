package com.netflix.spinnaker.config;

import com.netflix.spinnaker.retrofit.RetrofitConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import retrofit.RestAdapter;

@Configuration
@EnableConfigurationProperties(RetrofitConfigurationProperties.class)
public class RetrofitConfiguration {
  @Bean
  public RestAdapter.LogLevel retrofitLogLevel(
      RetrofitConfigurationProperties retrofitConfigurationProperties) {
    return retrofitConfigurationProperties.getLogLevel();
  }
}
