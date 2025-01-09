package com.netflix.spinnaker.echo.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spinnaker.config.OkHttp3ClientConfiguration;
import com.netflix.spinnaker.echo.services.KeelService;
import com.netflix.spinnaker.kork.retrofit.ErrorHandlingExecutorCallAdapterFactory;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import retrofit2.Retrofit;
import retrofit2.converter.jackson.JacksonConverterFactory;

@Configuration
@Slf4j
@ConditionalOnExpression("${keel.enabled:false}")
public class KeelConfig {

  @Bean
  public KeelService keelService(
      @Value("${keel.base-url}") String keelBaseUrl,
      OkHttp3ClientConfiguration okHttpClientConfig) {
    return new Retrofit.Builder()
        .baseUrl(keelBaseUrl)
        .client(okHttpClientConfig.createForRetrofit2().build())
        .addCallAdapterFactory(ErrorHandlingExecutorCallAdapterFactory.getInstance())
        .addConverterFactory(JacksonConverterFactory.create(new ObjectMapper()))
        .build()
        .create(KeelService.class);
  }
}
