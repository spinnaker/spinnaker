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
import com.netflix.spinnaker.kork.plugins.update.downloader.FileDownloaderProvider;
import com.netflix.spinnaker.kork.plugins.update.downloader.Front50FileDownloader;
import com.netflix.spinnaker.kork.plugins.update.internal.Front50Service;
import com.netflix.spinnaker.kork.plugins.update.internal.PluginOkHttpClientProvider;
import com.netflix.spinnaker.kork.plugins.update.repository.Front50UpdateRepository;
import com.netflix.spinnaker.okhttp.OkHttpClientConfigurationProperties;
import java.net.URL;
import java.util.Map;
import okhttp3.OkHttpClient;
import org.pf4j.update.UpdateRepository;
import org.pf4j.update.verifier.CompoundVerifier;
import org.springframework.beans.factory.BeanCreationException;
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

  @Bean
  public static PluginOkHttpClientProvider pluginsOkHttpClient(Environment environment) {
    OkHttpClientConfigurationProperties okHttpClientProperties =
        Binder.get(environment)
            .bind("ok-http-client", Bindable.of(OkHttpClientConfigurationProperties.class))
            .orElseThrow(
                () ->
                    new BeanCreationException(
                        "Unable to bind ok-http-client property to "
                            + OkHttpClientConfigurationProperties.class.getSimpleName()));

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
  public static UpdateRepository pluginFront50UpdateRepository(
      Environment environment,
      PluginOkHttpClientProvider pluginsOkHttpClientProvider,
      Map<String, PluginRepositoryProperties> pluginRepositoriesConfig,
      FileDownloaderProvider fileDownloaderProvider) {

    PluginRepositoryProperties front50RepositoryProps =
        pluginRepositoriesConfig.get(PluginsConfigurationProperties.FRONT5O_REPOSITORY);

    URL front50Url = getFront50Url(environment, front50RepositoryProps);

    ObjectMapper objectMapper =
        new ObjectMapper()
            .registerModule(new KotlinModule())
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            .configure(SerializationFeature.INDENT_OUTPUT, true)
            .setSerializationInclusion(JsonInclude.Include.NON_NULL);

    Front50Service front50Service =
        new Retrofit.Builder()
            .addConverterFactory(JacksonConverterFactory.create(objectMapper))
            .baseUrl(front50Url)
            .client(pluginsOkHttpClientProvider.getOkHttpClient())
            .build()
            .create(Front50Service.class);

    return new Front50UpdateRepository(
        PluginsConfigurationProperties.FRONT5O_REPOSITORY,
        front50Url,
        fileDownloaderProvider.get(front50RepositoryProps.fileDownloader),
        new CompoundVerifier(),
        front50Service);
  }

  /**
   * We are a bit inconsistent with how we configure service URLs, so we proceed in this order:
   *
   * <p>1) {@code front50.base-url} 2) {@code services.front50.base-url} 3) {@code
   * spinnaker.extensibility.repositories.front50.url}
   *
   * @param environment The Spring environment
   * @param front50RepositoryProps Front50 update repository configuration
   * @return The configured Front50 URL
   */
  private static URL getFront50Url(
      Environment environment, PluginRepositoryProperties front50RepositoryProps) {
    return Binder.get(environment)
        .bind("front50.base-url", Bindable.of(URL.class))
        .orElseGet(
            () ->
                Binder.get(environment)
                    .bind("services.front50.base-url", Bindable.of(URL.class))
                    .orElseGet(front50RepositoryProps::getUrl));
  }
}
