package com.netflix.spinnaker.echo.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spectator.api.Registry;
import com.netflix.spinnaker.config.OkHttp3ClientConfiguration;
import com.netflix.spinnaker.echo.jackson.EchoObjectMapper;
import com.netflix.spinnaker.echo.pipelinetriggers.PipelineCacheConfigurationProperties;
import com.netflix.spinnaker.echo.pipelinetriggers.eventhandlers.PubsubEventHandler;
import com.netflix.spinnaker.echo.pipelinetriggers.orca.OrcaService;
import com.netflix.spinnaker.echo.pipelinetriggers.runas.RunAsTokenClient;
import com.netflix.spinnaker.echo.pipelinetriggers.runas.RunAsTokenService;
import com.netflix.spinnaker.kork.expressions.config.ExpressionProperties;
import com.netflix.spinnaker.kork.retrofit.ErrorHandlingExecutorCallAdapterFactory;
import com.netflix.spinnaker.kork.retrofit.util.RetrofitUtils;
import com.netflix.spinnaker.security.s2s.client.ServiceIdentityInterceptor;
import com.netflix.spinnaker.security.s2s.config.ServiceIdentityClientConfiguration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import retrofit2.Retrofit;
import retrofit2.converter.jackson.JacksonConverterFactory;

@Slf4j
@Configuration
@ComponentScan(value = "com.netflix.spinnaker.echo.pipelinetriggers")
@Import(ServiceIdentityClientConfiguration.class)
@EnableConfigurationProperties({
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
  PubsubEventHandler pubsubEventHandler(Registry registry, ObjectMapper objectMapper) {
    return new PubsubEventHandler(registry, objectMapper);
  }

  /**
   * Retrofit client for Front50's run-as token mint/exchange endpoint (Component 7). Echo exchanges
   * a managed service-account name for a short-lived signed identity token rather than resolving SA
   * roles remotely or holding a signing key. Mirrors {@code Front50Service} wiring.
   */
  @Bean
  public RunAsTokenClient runAsTokenClient(
      @Value("${front50.base-url}") final String endpoint,
      ServiceIdentityInterceptor serviceIdentityInterceptor) {
    return new Retrofit.Builder()
        .baseUrl(RetrofitUtils.getBaseUrl(endpoint))
        .client(
            okHttp3ClientConfiguration
                .createForRetrofit2()
                .addInterceptor(serviceIdentityInterceptor)
                .build())
        .addCallAdapterFactory(ErrorHandlingExecutorCallAdapterFactory.getInstance())
        .addConverterFactory(JacksonConverterFactory.create(EchoObjectMapper.getInstance()))
        .build()
        .create(RunAsTokenClient.class);
  }

  /**
   * Echo holds <b>no</b> identity-token signing key. It proves it is a trusted caller of Front50's
   * initial run-as mint via service-to-service caller authentication (mTLS / mesh / Kubernetes
   * ServiceAccount token; see {@code authz.s2s}), not a shared-key assertion — so a compromised
   * Echo pod cannot extract a minting key and forge user identity tokens. The mint request carries
   * only the service account and pipeline id, which Front50 verifies against the saved pipeline's
   * {@code runAsUser}.
   *
   * <p>Because the shared-key assertion is gone, minting a run-as token requires {@code
   * authz.s2s.enabled=true} (so Front50 can authenticate Echo as the caller).
   */
  @Bean
  public RunAsTokenService runAsTokenService(RunAsTokenClient runAsTokenClient) {
    return new RunAsTokenService(runAsTokenClient);
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
