package com.netflix.spinnaker.echo.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spectator.api.Registry;
import com.netflix.spinnaker.config.OkHttp3ClientConfiguration;
import com.netflix.spinnaker.echo.jackson.EchoObjectMapper;
import com.netflix.spinnaker.echo.pipelinetriggers.PipelineCacheConfigurationProperties;
import com.netflix.spinnaker.echo.pipelinetriggers.eventhandlers.PubsubEventHandler;
import com.netflix.spinnaker.echo.pipelinetriggers.orca.OrcaService;
import com.netflix.spinnaker.fiat.shared.FiatClientConfigurationProperties;
import com.netflix.spinnaker.fiat.shared.FiatPermissionEvaluator;
import com.netflix.spinnaker.fiat.shared.FiatStatus;
import com.netflix.spinnaker.kork.dynamicconfig.DynamicConfigService;
import com.netflix.spinnaker.kork.expressions.config.ExpressionProperties;
import com.netflix.spinnaker.kork.retrofit.ErrorHandlingExecutorCallAdapterFactory;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import retrofit2.Retrofit;
import retrofit2.converter.jackson.JacksonConverterFactory;

@Slf4j
@Configuration
@ComponentScan(value = "com.netflix.spinnaker.echo.pipelinetriggers")
@EnableConfigurationProperties({
  FiatClientConfigurationProperties.class,
  PipelineCacheConfigurationProperties.class,
  QuietPeriodIndicatorConfigurationProperties.class,
  ExpressionProperties.class
})
public class PipelineTriggerConfiguration {
  private OkHttp3ClientConfiguration okHttp3ClientConfiguration;

  @Value("${trigger.git.shared-secret:}")
  private String gitSharedSecret;

  @Autowired
  public void setOkHttp3ClientConfiguration(OkHttp3ClientConfiguration okHttp3ClientConfiguration) {
    this.okHttp3ClientConfiguration = okHttp3ClientConfiguration;
  }

  public String getGitSharedSecret() {
    return this.gitSharedSecret;
  }

  @Bean
  public OrcaService orca(@Value("${orca.base-url}") final String endpoint) {
    return bindRetrofitService(OrcaService.class, endpoint);
  }

  @Bean
  public FiatStatus fiatStatus(
      Registry registry,
      DynamicConfigService dynamicConfigService,
      FiatClientConfigurationProperties fiatClientConfigurationProperties) {
    return new FiatStatus(registry, dynamicConfigService, fiatClientConfigurationProperties);
  }

  @Bean
  PubsubEventHandler pubsubEventHandler(
      Registry registry,
      ObjectMapper objectMapper,
      FiatPermissionEvaluator fiatPermissionEvaluator) {
    return new PubsubEventHandler(registry, objectMapper, fiatPermissionEvaluator);
  }

  @Bean
  public ExecutorService executorService(
      @Value("${orca.pipeline-initiator-threadpool-size:16}") int threadPoolSize) {
    return Executors.newFixedThreadPool(threadPoolSize);
  }

  private <T> T bindRetrofitService(final Class<T> type, final String endpoint) {
    log.info("Connecting {} to {}", type.getSimpleName(), endpoint);

    return new Retrofit.Builder()
        .baseUrl(endpoint)
        .client(okHttp3ClientConfiguration.createForRetrofit2().build())
        .addCallAdapterFactory(ErrorHandlingExecutorCallAdapterFactory.getInstance())
        .addConverterFactory(JacksonConverterFactory.create(EchoObjectMapper.getInstance()))
        .build()
        .create(type);
  }
}
