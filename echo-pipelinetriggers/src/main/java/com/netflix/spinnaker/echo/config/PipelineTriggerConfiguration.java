package com.netflix.spinnaker.echo.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spectator.api.Registry;
import com.netflix.spinnaker.echo.model.Pipeline;
import com.netflix.spinnaker.echo.pipelinetriggers.PipelineCache;
import com.netflix.spinnaker.echo.pipelinetriggers.monitor.PubsubEventMonitor;
import com.netflix.spinnaker.echo.pipelinetriggers.orca.OrcaService;
import com.netflix.spinnaker.retrofit.Slf4jRetrofitLogger;
import com.squareup.okhttp.OkHttpClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import retrofit.RestAdapter;
import retrofit.client.Client;
import retrofit.client.OkClient;
import retrofit.converter.JacksonConverter;
import rx.Scheduler;
import rx.functions.Action1;
import rx.schedulers.Schedulers;

@Configuration
@ComponentScan("com.netflix.spinnaker.echo.pipelinetriggers")
@Slf4j
public class PipelineTriggerConfiguration {
  private Client retrofitClient;

  @Autowired
  public void setRetrofitClient(OkHttpClient okHttpClient) {
    this.retrofitClient = new OkClient(okHttpClient);
  }

  @Bean
  public OrcaService orca(@Value("${orca.baseUrl}") final String endpoint) {
    return bindRetrofitService(OrcaService.class, endpoint);
  }

  @Bean
  public Scheduler scheduler() {
    return Schedulers.io();
  }

  @Bean
  public int pollingIntervalSeconds() {
    return 10;
  }

  @Bean
  public Client retrofitClient() {
    return new OkClient();
  }

  @Bean
  @ConditionalOnMissingBean(PubsubEventMonitor.class)
  PubsubEventMonitor pubsubEventMonitor(PipelineCache pipelineCache, Action1<Pipeline> subscriber, Registry registry) {
    return new PubsubEventMonitor(pipelineCache, subscriber, registry);
  }

  private <T> T bindRetrofitService(final Class<T> type, final String endpoint) {
    log.info("Connecting {} to {}", type.getSimpleName(), endpoint);

    return new RestAdapter.Builder().setClient(retrofitClient)
                                    .setConverter(new JacksonConverter(new ObjectMapper()))
                                    .setEndpoint(endpoint)
                                    .setLog(new Slf4jRetrofitLogger(type))
                                    .build()
                                    .create(type);
  }
}
