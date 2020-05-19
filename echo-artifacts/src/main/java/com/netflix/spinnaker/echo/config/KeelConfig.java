package com.netflix.spinnaker.echo.config;

import com.jakewharton.retrofit.Ok3Client;
import com.netflix.spinnaker.config.DefaultServiceEndpoint;
import com.netflix.spinnaker.config.okhttp3.OkHttpClientProvider;
import com.netflix.spinnaker.echo.services.KeelService;
import com.netflix.spinnaker.retrofit.Slf4jRetrofitLogger;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import retrofit.Endpoint;
import retrofit.Endpoints;
import retrofit.RestAdapter;
import retrofit.RestAdapter.LogLevel;
import retrofit.converter.JacksonConverter;

@Configuration
@Slf4j
@ConditionalOnExpression("${keel.enabled:false}")
public class KeelConfig {
  @Bean
  public LogLevel retrofitLogLevel(@Value("${retrofit.log-level:BASIC}") String retrofitLogLevel) {
    return LogLevel.valueOf(retrofitLogLevel);
  }

  @Bean
  public Endpoint keelEndpoint(@Value("${keel.base-url}") String keelBaseUrl) {
    return Endpoints.newFixedEndpoint(keelBaseUrl);
  }

  @Bean
  public KeelService keelService(
      Endpoint keelEndpoint, OkHttpClientProvider clientProvider, LogLevel retrofitLogLevel) {
    return new RestAdapter.Builder()
        .setEndpoint(keelEndpoint)
        .setConverter(new JacksonConverter())
        .setClient(
            new Ok3Client(
                clientProvider.getClient(
                    new DefaultServiceEndpoint("keel", keelEndpoint.getUrl()))))
        .setLogLevel(retrofitLogLevel)
        .setLog(new Slf4jRetrofitLogger(KeelService.class))
        .build()
        .create(KeelService.class);
  }
}
