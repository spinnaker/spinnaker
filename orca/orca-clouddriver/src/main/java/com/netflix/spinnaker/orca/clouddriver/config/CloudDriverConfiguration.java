/*
 * Copyright 2015 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.orca.clouddriver.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spinnaker.config.DefaultServiceEndpoint;
import com.netflix.spinnaker.kork.artifacts.artifactstore.ArtifactStoreConfiguration;
import com.netflix.spinnaker.kork.client.ServiceClientProvider;
import com.netflix.spinnaker.kork.core.RetrySupport;
import com.netflix.spinnaker.kork.web.selector.DefaultServiceSelector;
import com.netflix.spinnaker.kork.web.selector.SelectableService;
import com.netflix.spinnaker.kork.web.selector.ServiceSelector;
import com.netflix.spinnaker.orca.api.operations.OperationsRunner;
import com.netflix.spinnaker.orca.clouddriver.*;
import com.netflix.spinnaker.orca.clouddriver.config.CloudDriverConfigurationProperties.BaseUrl;
import com.netflix.spinnaker.orca.jackson.OrcaObjectMapper;
import com.netflix.spinnaker.orca.retrofit.RetrofitConfiguration;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

@Configuration
@Import({RetrofitConfiguration.class, ArtifactStoreConfiguration.class})
@ComponentScan({
  "com.netflix.spinnaker.orca.clouddriver",
  "com.netflix.spinnaker.orca.kato.pipeline",
  "com.netflix.spinnaker.orca.kato.tasks"
})
@EnableConfigurationProperties({
  CloudDriverConfigurationProperties.class,
  PollerConfigurationProperties.class,
  TaskConfigurationProperties.class
})
@Slf4j
public class CloudDriverConfiguration {

  @ConditionalOnMissingBean(ObjectMapper.class)
  @Bean
  ObjectMapper mapper() {
    return OrcaObjectMapper.newInstance();
  }

  @Bean
  public ClouddriverRetrofitBuilder clouddriverRetrofitBuilder(
      ObjectMapper objectMapper,
      ServiceClientProvider serviceClientProvider,
      CloudDriverConfigurationProperties cloudDriverConfigurationProperties) {
    return new ClouddriverRetrofitBuilder(
        objectMapper, serviceClientProvider, cloudDriverConfigurationProperties);
  }

  public static class ClouddriverRetrofitBuilder {
    private final ObjectMapper objectMapper;
    private final ServiceClientProvider serviceClientProvider;
    private final CloudDriverConfigurationProperties cloudDriverConfigurationProperties;

    ClouddriverRetrofitBuilder(
        ObjectMapper objectMapper,
        ServiceClientProvider serviceClientProvider,
        CloudDriverConfigurationProperties cloudDriverConfigurationProperties) {
      this.objectMapper = objectMapper;
      this.serviceClientProvider = serviceClientProvider;
      this.cloudDriverConfigurationProperties = cloudDriverConfigurationProperties;
    }

    /**
     * Builds a {@link SelectableService} for a read Clouddriver endpoint. This method gets any
     * readonly URLs configured in the profile and then for each constructs a {@link
     * ServiceSelector} based on the criteria configured.
     *
     * @param type the class to construct
     * @param <T> the type of class to construct
     * @return a {@link SelectableService} configured with the {@link ServiceSelector} instances
     *     from the profile
     */
    private <T> SelectableService buildReadOnlyService(Class<T> type) {
      List<String> urls =
          cloudDriverConfigurationProperties.getCloudDriverReadOnlyBaseUrls().stream()
              .map(CloudDriverConfigurationProperties.BaseUrl::getBaseUrl)
              .collect(Collectors.toList());

      if (urls.isEmpty()
          || urls.stream()
              .allMatch(
                  url -> url.equals(cloudDriverConfigurationProperties.getCloudDriverBaseUrl()))) {
        log.info(
            "readonly URL not configured for clouddriver, using default clouddriver {} for {}",
            cloudDriverConfigurationProperties.getCloudDriverBaseUrl(),
            type.getSimpleName());
      }

      List<ServiceSelector> selectors =
          getServiceSelectors(
              cloudDriverConfigurationProperties.getCloudDriverReadOnlyBaseUrls(), type);
      return new SelectableService(selectors);
    }

    /**
     * Builds a {@link SelectableService} for a write Clouddriver endpoint. This method gets any
     * writeonly URLs configured in the profile and then for each constructs a {@link
     * ServiceSelector} based on the criteria configured.
     *
     * @param type the class to construct
     * @param <T> the type of class to construct
     * @return a {@link SelectableService} configured with the {@link ServiceSelector} instances
     *     from the profile
     */
    public <T> SelectableService buildWriteableService(Class<T> type) {
      List<String> urls =
          cloudDriverConfigurationProperties.getCloudDriverWriteOnlyBaseUrls().stream()
              .map(CloudDriverConfigurationProperties.BaseUrl::getBaseUrl)
              .collect(Collectors.toList());

      if (urls.isEmpty()
          || urls.stream()
              .allMatch(
                  url -> url.equals(cloudDriverConfigurationProperties.getCloudDriverBaseUrl()))) {
        log.info(
            "writeonly URL not configured for clouddriver, using default clouddriver {} for {}",
            cloudDriverConfigurationProperties.getCloudDriverBaseUrl(),
            type.getSimpleName());
      }

      List<ServiceSelector> selectors =
          getServiceSelectors(
              cloudDriverConfigurationProperties.getCloudDriverWriteOnlyBaseUrls(), type);
      return new SelectableService(selectors);
    }

    private <T> List<ServiceSelector> getServiceSelectors(List<BaseUrl> baseUrls, Class<T> type) {
      List<ServiceSelector> serviceSelectors = new ArrayList<>();

      baseUrls.forEach(
          url -> {
            ServiceSelector selector =
                new DefaultServiceSelector(
                    buildService(type, url.getBaseUrl()), url.getPriority(), url.getConfig());

            if (url.getConfig() != null && url.getConfig().get("selectorClass") != null) {
              try {
                Class<ServiceSelector> selectorClass =
                    (Class<ServiceSelector>)
                        Class.forName(url.getConfig().get("selectorClass").toString());
                selector =
                    (ServiceSelector)
                        selectorClass.getConstructors()[0].newInstance(
                            selector.getService(), selector.getPriority(), url.getConfig());
              } catch (Exception e) {
                log.error(
                    "Failed to create selector for class {}", url.getConfig().get("selectorClass"));

                throw new RuntimeException(e);
              }
            }

            serviceSelectors.add(selector);
          });

      return serviceSelectors;
    }

    public <T> T buildService(Class<T> type, String url) {
      return serviceClientProvider.getService(
          type, new DefaultServiceEndpoint(type.getSimpleName(), url), objectMapper);
    }

    @Bean
    public MortService mortDeployService(ClouddriverRetrofitBuilder clouddriverRetrofitBuilder) {
      return new DelegatingMortService(
          clouddriverRetrofitBuilder.buildReadOnlyService(MortService.class));
    }

    @Bean
    public CloudDriverCacheService clouddriverCacheService(
        ClouddriverRetrofitBuilder clouddriverRetrofitBuilder) {
      return new DelegatingClouddriverCacheService(
          clouddriverRetrofitBuilder.buildWriteableService(CloudDriverCacheService.class));
    }

    @Bean
    public CloudDriverCacheStatusService cloudDriverCacheStatusService(
        ClouddriverRetrofitBuilder clouddriverRetrofitBuilder) {
      return new DelegatingCloudDriverCacheStatusService(
          clouddriverRetrofitBuilder.buildReadOnlyService(CloudDriverCacheStatusService.class));
    }

    @Bean
    public OortService oortDeployService(ClouddriverRetrofitBuilder clouddriverRetrofitBuilder) {
      return new DelegatingOortService(
          clouddriverRetrofitBuilder.buildReadOnlyService(OortService.class));
    }

    @Bean
    public CloudDriverTaskStatusService cloudDriverTaskStatusService(
        ClouddriverRetrofitBuilder clouddriverRetrofitBuilder) {
      return new DelegatingCloudDriverTaskStatusService(
          clouddriverRetrofitBuilder.buildReadOnlyService(CloudDriverTaskStatusService.class));
    }

    @Bean
    public KatoRestService katoDeployService(
        ClouddriverRetrofitBuilder clouddriverRetrofitBuilder) {
      return new DelegatingKatoRestService(
          clouddriverRetrofitBuilder.buildWriteableService(KatoRestService.class));
    }

    @Bean
    KatoService katoService(
        KatoRestService katoRestService,
        CloudDriverTaskStatusService cloudDriverTaskStatusService,
        RetrySupport retrySupport,
        ObjectMapper objectMapper) {
      return new KatoService(
          katoRestService, cloudDriverTaskStatusService, retrySupport, objectMapper);
    }

    @Bean
    @ConditionalOnMissingBean(OperationsRunner.class)
    OperationsRunner katoOperationsRunner(KatoService katoService) {
      return new KatoOperationsRunner(katoService);
    }

    @Bean
    FeaturesRestService featuresRestService(ClouddriverRetrofitBuilder clouddriverRetrofitBuilder) {
      return new DelegatingFeaturesRestService(
          clouddriverRetrofitBuilder.buildWriteableService(FeaturesRestService.class));
    }
  }
}
