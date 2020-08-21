/*
 * Copyright 2019 Netflix, Inc.
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

import com.fasterxml.jackson.core.type.TypeReference;
import com.netflix.spectator.api.Registry;
import com.netflix.spinnaker.config.PluginsConfigurationProperties.PluginRepositoryProperties;
import com.netflix.spinnaker.kork.annotations.Beta;
import com.netflix.spinnaker.kork.dynamicconfig.DynamicConfigService;
import com.netflix.spinnaker.kork.dynamicconfig.SpringDynamicConfigService;
import com.netflix.spinnaker.kork.plugins.*;
import com.netflix.spinnaker.kork.plugins.actuator.InstalledPluginsEndpoint;
import com.netflix.spinnaker.kork.plugins.bundle.PluginBundleExtractor;
import com.netflix.spinnaker.kork.plugins.config.ConfigFactory;
import com.netflix.spinnaker.kork.plugins.config.ConfigResolver;
import com.netflix.spinnaker.kork.plugins.config.RepositoryConfigCoordinates;
import com.netflix.spinnaker.kork.plugins.config.SpringEnvironmentConfigResolver;
import com.netflix.spinnaker.kork.plugins.proxy.aspects.LogInvocationAspect;
import com.netflix.spinnaker.kork.plugins.proxy.aspects.MetricInvocationAspect;
import com.netflix.spinnaker.kork.plugins.sdk.SdkFactory;
import com.netflix.spinnaker.kork.plugins.update.SpinnakerUpdateManager;
import com.netflix.spinnaker.kork.plugins.update.downloader.CompositeFileDownloader;
import com.netflix.spinnaker.kork.plugins.update.downloader.FileDownloaderProvider;
import com.netflix.spinnaker.kork.plugins.update.downloader.SupportingFileDownloader;
import com.netflix.spinnaker.kork.plugins.update.release.provider.AggregatePluginInfoReleaseProvider;
import com.netflix.spinnaker.kork.plugins.update.release.provider.PluginInfoReleaseProvider;
import com.netflix.spinnaker.kork.plugins.update.release.remote.RemotePluginInfoReleaseCache;
import com.netflix.spinnaker.kork.plugins.update.release.source.Front50PluginInfoReleaseSource;
import com.netflix.spinnaker.kork.plugins.update.release.source.LatestPluginInfoReleaseSource;
import com.netflix.spinnaker.kork.plugins.update.release.source.PluginInfoReleaseSource;
import com.netflix.spinnaker.kork.plugins.update.release.source.PreferredPluginInfoReleaseSource;
import com.netflix.spinnaker.kork.plugins.update.release.source.SpringPluginInfoReleaseSource;
import com.netflix.spinnaker.kork.plugins.update.repository.ConfigurableUpdateRepository;
import com.netflix.spinnaker.kork.version.ServiceVersion;
import com.netflix.spinnaker.kork.version.SpringPackageVersionResolver;
import com.netflix.spinnaker.kork.version.VersionResolver;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;
import org.pf4j.PluginFactory;
import org.pf4j.PluginStatusProvider;
import org.pf4j.VersionManager;
import org.pf4j.update.UpdateRepository;
import org.pf4j.update.verifier.CompoundVerifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.Environment;

@EnableConfigurationProperties(PluginsConfigurationProperties.class)
@Import({
  V1PluginConfiguration.class,
  V2PluginConfiguration.class,
  Front50PluginsConfiguration.class,
  RemotePluginsConfiguration.class
})
public class PluginsAutoConfiguration {

  private static final Logger log = LoggerFactory.getLogger(PluginsAutoConfiguration.class);

  @Bean
  @ConditionalOnMissingBean(DynamicConfigService.class)
  DynamicConfigService springTransientConfigService() {
    return new SpringDynamicConfigService();
  }

  @Bean
  public static SpringPluginStatusProvider pluginStatusProvider(
      DynamicConfigService dynamicConfigService) {
    String configNamespace = PluginsConfigurationProperties.CONFIG_NAMESPACE;
    String defaultRootPath = PluginsConfigurationProperties.DEFAULT_ROOT_PATH;
    return new SpringPluginStatusProvider(
        dynamicConfigService, configNamespace + "." + defaultRootPath);
  }

  @Bean
  @ConditionalOnMissingBean(VersionResolver.class)
  public static VersionResolver versionResolver(ApplicationContext applicationContext) {
    return new SpringPackageVersionResolver(applicationContext);
  }

  @Bean
  @ConditionalOnMissingBean(ServiceVersion.class)
  public static ServiceVersion serviceVersion(
      ApplicationContext applicationContext, List<VersionResolver> versionResolvers) {
    return new ServiceVersion(applicationContext, versionResolvers);
  }

  @Bean
  public static VersionManager versionManager(ApplicationContext applicationContext) {
    return new SpinnakerServiceVersionManager(
        Objects.requireNonNull(
            applicationContext.getEnvironment().getProperty("spring.application.name")));
  }

  @Bean
  @ConditionalOnMissingBean(ConfigResolver.class)
  public static ConfigResolver springEnvironmentConfigResolver(
      ConfigurableEnvironment environment) {
    return new SpringEnvironmentConfigResolver(environment);
  }

  @Bean
  ConfigFactory configFactory(ConfigResolver configResolver) {
    return new ConfigFactory(configResolver);
  }

  @Bean
  public static Map<String, PluginRepositoryProperties> pluginRepositoriesConfig(
      ConfigResolver configResolver) {
    return configResolver.resolve(
        new RepositoryConfigCoordinates(),
        new TypeReference<HashMap<String, PluginRepositoryProperties>>() {});
  }

  @Bean
  public static SpinnakerPluginManager pluginManager(
      ServiceVersion serviceVersion,
      VersionManager versionManager,
      PluginStatusProvider pluginStatusProvider,
      ApplicationContext applicationContext,
      ConfigFactory configFactory,
      List<SdkFactory> sdkFactories,
      PluginBundleExtractor pluginBundleExtractor,
      PluginFactory pluginFactory) {
    return new SpinnakerPluginManager(
        serviceVersion,
        versionManager,
        pluginStatusProvider,
        configFactory,
        sdkFactories,
        Objects.requireNonNull(
            applicationContext.getEnvironment().getProperty("spring.application.name")),
        determineRootPluginPath(applicationContext),
        pluginBundleExtractor,
        pluginFactory);
  }

  /**
   * If the plugins-root-path property is set, returns the absolute path to the property. Otherwise,
   * returns the default root path 'plugins'.
   */
  private static Path determineRootPluginPath(ApplicationContext applicationContext) {
    String rootPathConfig =
        applicationContext
            .getEnvironment()
            .getProperty(PluginsConfigurationProperties.ROOT_PATH_CONFIG);
    return rootPathConfig == null
        ? Paths.get(PluginsConfigurationProperties.DEFAULT_ROOT_PATH)
        : Paths.get(rootPathConfig).toAbsolutePath();
  }

  @Bean
  public static PluginBundleExtractor pluginBundleExtractor(
      SpringStrictPluginLoaderStatusProvider springStrictPluginLoaderStatusProvider) {
    return new PluginBundleExtractor(springStrictPluginLoaderStatusProvider);
  }

  @Bean
  public static PluginInfoReleaseSource springPluginInfoReleaseSource(
      SpringPluginStatusProvider pluginStatusProvider) {
    return new SpringPluginInfoReleaseSource(pluginStatusProvider);
  }

  @Bean
  public static PluginInfoReleaseSource latestPluginInfoReleaseSource(
      SpinnakerUpdateManager updateManager) {
    return new LatestPluginInfoReleaseSource(updateManager, null);
  }

  @Bean
  public static PluginInfoReleaseSource preferredPluginInfoReleaseSource() {
    return new PreferredPluginInfoReleaseSource();
  }

  @Bean
  public static PluginInfoReleaseProvider pluginInfoReleaseProvider(
      List<PluginInfoReleaseSource> pluginInfoReleaseSources,
      SpringStrictPluginLoaderStatusProvider springStrictPluginLoaderStatusProvider) {
    return new AggregatePluginInfoReleaseProvider(
        pluginInfoReleaseSources, springStrictPluginLoaderStatusProvider);
  }

  /** Not a static bean - see {@link RemotePluginsConfiguration}. */
  @Bean
  @Beta
  public RemotePluginInfoReleaseCache remotePluginInfoReleaseCache(
      Collection<PluginInfoReleaseSource> pluginInfoReleaseSources,
      SpringStrictPluginLoaderStatusProvider springStrictPluginLoaderStatusProvider,
      ApplicationEventPublisher applicationEventPublisher,
      SpinnakerUpdateManager updateManager,
      SpinnakerPluginManager pluginManager,
      SpringPluginStatusProvider springPluginStatusProvider) {
    return new RemotePluginInfoReleaseCache(
        new AggregatePluginInfoReleaseProvider(
            pluginInfoReleaseSources.stream()
                .filter(source -> !(source instanceof Front50PluginInfoReleaseSource))
                .collect(Collectors.toList()),
            springStrictPluginLoaderStatusProvider),
        applicationEventPublisher,
        updateManager,
        pluginManager,
        springPluginStatusProvider);
  }

  @Bean
  public static SpinnakerUpdateManager pluginUpdateManager(
      SpinnakerPluginManager pluginManager,
      ApplicationEventPublisher applicationEventPublisher,
      List<UpdateRepository> updateRepositories) {
    return new SpinnakerUpdateManager(applicationEventPublisher, pluginManager, updateRepositories);
  }

  @Bean
  public static FileDownloaderProvider fileDownloaderProvider(
      List<SupportingFileDownloader> fileDownloaders) {
    return new FileDownloaderProvider(new CompositeFileDownloader(fileDownloaders));
  }

  @Bean
  public static List<UpdateRepository> pluginUpdateRepositories(
      Map<String, PluginRepositoryProperties> pluginRepositoriesConfig,
      FileDownloaderProvider fileDownloaderProvider) {

    List<UpdateRepository> repositories =
        pluginRepositoriesConfig.entrySet().stream()
            .filter(entry -> entry.getValue().isEnabled())
            .filter(
                entry -> !entry.getKey().equals(PluginsConfigurationProperties.FRONT5O_REPOSITORY))
            .map(
                entry ->
                    new ConfigurableUpdateRepository(
                        entry.getKey(),
                        entry.getValue().getUrl(),
                        fileDownloaderProvider.get(entry.getValue().fileDownloader),
                        new CompoundVerifier()))
            .collect(Collectors.toList());

    if (repositories.isEmpty()) {
      log.warn(
          "No remote repositories defined, will fallback to looking for a "
              + "'repositories.json' file next to the application executable");
    }

    return repositories;
  }

  @Bean
  public static SpringStrictPluginLoaderStatusProvider springStrictPluginLoaderStatusProvider(
      Environment environment) {
    return new SpringStrictPluginLoaderStatusProvider(environment);
  }

  @Bean
  public static MetricInvocationAspect metricInvocationAspect(
      ObjectProvider<Registry> registryProvider) {
    return new MetricInvocationAspect(registryProvider);
  }

  @Bean
  public static LogInvocationAspect logInvocationAspect() {
    return new LogInvocationAspect();
  }

  @Bean
  public static InstalledPluginsEndpoint installedPluginsEndpoint(
      SpinnakerPluginManager pluginManager) {
    return new InstalledPluginsEndpoint(pluginManager);
  }
}
