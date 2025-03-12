/*
 * Copyright 2020 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.config;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.module.kotlin.KotlinModule;
import com.netflix.spinnaker.config.PluginsConfigurationProperties.PluginRepositoryProperties;
import com.netflix.spinnaker.kork.plugins.update.EnvironmentServerGroupLocationResolver;
import com.netflix.spinnaker.kork.plugins.update.EnvironmentServerGroupNameResolver;
import com.netflix.spinnaker.kork.plugins.update.ServerGroupLocationResolver;
import com.netflix.spinnaker.kork.plugins.update.ServerGroupNameResolver;
import com.netflix.spinnaker.kork.plugins.update.downloader.FileDownloaderProvider;
import com.netflix.spinnaker.kork.plugins.update.downloader.Front50FileDownloader;
import com.netflix.spinnaker.kork.plugins.update.internal.Front50Service;
import com.netflix.spinnaker.kork.plugins.update.internal.PluginOkHttpClientProvider;
import com.netflix.spinnaker.kork.plugins.update.release.source.Front50PluginInfoReleaseSource;
import com.netflix.spinnaker.kork.plugins.update.release.source.PluginInfoReleaseSource;
import com.netflix.spinnaker.kork.plugins.update.repository.Front50UpdateRepository;
import com.netflix.spinnaker.okhttp.OkHttpClientConfigurationProperties;
import java.net.URL;
import java.util.Map;
import java.util.Objects;
import okhttp3.OkHttpClient;
import org.pf4j.update.UpdateRepository;
import org.pf4j.update.verifier.CompoundVerifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import retrofit2.Retrofit;
import retrofit2.converter.jackson.JacksonConverterFactory;

@Configuration
@ConditionalOnProperty("spinnaker.extensibility.repositories.front50.enabled")
public class Front50PluginsConfiguration {

  private static final Logger log = LoggerFactory.getLogger(Front50PluginsConfiguration.class);

  @Bean
  public static PluginOkHttpClientProvider pluginsOkHttpClient(Environment environment) {
    OkHttpClientConfigurationProperties okHttpClientProperties =
        Binder.get(environment)
            .bind("ok-http-client", Bindable.of(OkHttpClientConfigurationProperties.class))
            .orElse(new OkHttpClientConfigurationProperties());

    OkHttpClient okHttpClient =
        new OkHttp3ClientConfiguration(okHttpClientProperties)
            .create()
            .retryOnConnectionFailure(okHttpClientProperties.isRetryOnConnectionFailure())
            .build();

    return new PluginOkHttpClientProvider(okHttpClient);
  }

  @Bean
  public static Front50FileDownloader front50FileDownloader(
      Environment environment,
      PluginOkHttpClientProvider pluginsOkHttpClientProvider,
      Map<String, PluginRepositoryProperties> pluginRepositoriesConfig) {
    PluginRepositoryProperties front50RepositoryProps =
        pluginRepositoriesConfig.get(PluginsConfigurationProperties.FRONT5O_REPOSITORY);

    URL front50Url = getFront50Url(environment, front50RepositoryProps);
    return new Front50FileDownloader(pluginsOkHttpClientProvider.getOkHttpClient(), front50Url);
  }

  @Bean
  public static Front50Service pluginFront50Service(
      Environment environment,
      PluginOkHttpClientProvider pluginsOkHttpClientProvider,
      Map<String, PluginRepositoryProperties> pluginRepositoriesConfig) {
    PluginRepositoryProperties front50RepositoryProps =
        pluginRepositoriesConfig.get(PluginsConfigurationProperties.FRONT5O_REPOSITORY);

    URL front50Url = getFront50Url(environment, front50RepositoryProps);

    KotlinModule kotlinModule = new KotlinModule.Builder().build();

    ObjectMapper objectMapper =
        new ObjectMapper()
            .registerModule(kotlinModule)
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            .configure(SerializationFeature.INDENT_OUTPUT, true)
            .setSerializationInclusion(JsonInclude.Include.NON_NULL);

    return new Retrofit.Builder()
        .addConverterFactory(JacksonConverterFactory.create(objectMapper))
        .baseUrl(front50Url)
        .client(pluginsOkHttpClientProvider.getOkHttpClient())
        .build()
        .create(Front50Service.class);
  }

  @Bean
  public static UpdateRepository pluginFront50UpdateRepository(
      Front50Service front50Service,
      Environment environment,
      Map<String, PluginRepositoryProperties> pluginRepositoriesConfig,
      FileDownloaderProvider fileDownloaderProvider) {

    PluginRepositoryProperties front50RepositoryProps =
        pluginRepositoriesConfig.get(PluginsConfigurationProperties.FRONT5O_REPOSITORY);

    URL front50Url = getFront50Url(environment, front50RepositoryProps);

    return new Front50UpdateRepository(
        PluginsConfigurationProperties.FRONT5O_REPOSITORY,
        front50Url,
        fileDownloaderProvider.get(front50RepositoryProps.fileDownloader),
        new CompoundVerifier(),
        front50Service);
  }

  @Bean
  public static PluginInfoReleaseSource front50PluginReleaseProvider(
      Front50Service front50Service, Environment environment) {
    String appName = environment.getProperty("spring.application.name");
    Objects.requireNonNull(appName, "spring.application.name property must be set");

    ServerGroupNameResolver nameResolver = new EnvironmentServerGroupNameResolver(environment);
    ServerGroupLocationResolver locationResolver =
        new EnvironmentServerGroupLocationResolver(environment);

    return new Front50PluginInfoReleaseSource(
        front50Service, nameResolver, locationResolver, appName);
  }

  /**
   * We are a bit inconsistent with how we configure service URLs, so we proceed in this order:
   *
   * <p>1) {@code spinnaker.extensibility.repositories.front50.url} 2) {@code front50.base-url} 3)
   * {@code services.front50.base-url}
   *
   * @param environment The Spring environment
   * @param front50RepositoryProps Front50 update repository configuration
   * @return The configured Front50 URL
   */
  private static URL getFront50Url(
      Environment environment, PluginRepositoryProperties front50RepositoryProps) {
    try {
      return front50RepositoryProps.getUrl();
    } catch (Exception e) {
      log.warn(
          "Front50 update repository URL is either not specified or malformed, falling back "
              + "to default configuration",
          e);
      return Binder.get(environment)
          .bind("front50.base-url", Bindable.of(URL.class))
          .orElseGet(
              () ->
                  Binder.get(environment)
                      .bind("services.front50.base-url", Bindable.of(URL.class))
                      .get());
    }
  }
}
