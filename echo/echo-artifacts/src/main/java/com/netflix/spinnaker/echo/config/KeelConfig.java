package com.netflix.spinnaker.echo.config;

import com.netflix.spinnaker.config.OkHttp3ClientConfiguration;
import com.netflix.spinnaker.echo.services.KeelService;
import com.netflix.spinnaker.kork.retrofit.ErrorHandlingExecutorCallAdapterFactory;
import com.netflix.spinnaker.kork.retrofit.util.CustomConverterFactory;
import com.netflix.spinnaker.kork.retrofit.util.RetrofitUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import retrofit2.Retrofit;

@Configuration
@Slf4j
@ConditionalOnExpression("${keel.enabled:false}")
public class KeelConfig {

  @Bean
  public KeelService keelService(
      @Value("${keel.base-url}") String keelBaseUrl,
      OkHttp3ClientConfiguration okHttpClientConfig) {
    return new Retrofit.Builder()
        .baseUrl(RetrofitUtils.getBaseUrl(keelBaseUrl))
        .client(okHttpClientConfig.createForRetrofit2().build())
        .addCallAdapterFactory(ErrorHandlingExecutorCallAdapterFactory.getInstance())
        .addConverterFactory(CustomConverterFactory.create())
        .build()
        .create(KeelService.class);
  }
}
