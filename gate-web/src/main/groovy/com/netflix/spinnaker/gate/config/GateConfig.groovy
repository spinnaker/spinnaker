/*
 * Copyright 2014 Netflix, Inc.
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

package com.netflix.spinnaker.gate.config

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.netflix.spectator.api.Registry
import com.netflix.spinnaker.config.DefaultServiceEndpoint
import com.netflix.spinnaker.config.OkHttp3ClientConfiguration
import com.netflix.spinnaker.config.PluginsAutoConfiguration
import com.netflix.spinnaker.config.okhttp3.OkHttpClientProvider
import com.netflix.spinnaker.fiat.shared.FiatClientConfigurationProperties
import com.netflix.spinnaker.fiat.shared.FiatPermissionEvaluator
import com.netflix.spinnaker.fiat.shared.FiatService
import com.netflix.spinnaker.fiat.shared.FiatStatus
import com.netflix.spinnaker.filters.AuthenticatedRequestFilter
import com.netflix.spinnaker.gate.config.PostConnectionConfiguringJedisConnectionFactory.ConnectionPostProcessor
import com.netflix.spinnaker.gate.converters.JsonHttpMessageConverter
import com.netflix.spinnaker.gate.converters.YamlHttpMessageConverter
import com.netflix.spinnaker.gate.filters.RequestLoggingFilter
import com.netflix.spinnaker.gate.filters.RequestSheddingFilter
import com.netflix.spinnaker.gate.filters.ResetAuthenticatedRequestFilter
import com.netflix.spinnaker.gate.plugins.deck.DeckPluginConfiguration
import com.netflix.spinnaker.gate.plugins.web.PluginWebConfiguration
import com.netflix.spinnaker.gate.services.EurekaLookupService
import com.netflix.spinnaker.gate.services.internal.*
import com.netflix.spinnaker.kork.client.ServiceClientProvider
import com.netflix.spinnaker.kork.dynamicconfig.DynamicConfigService
import com.netflix.spinnaker.kork.web.context.AuthenticatedRequestContextProvider
import com.netflix.spinnaker.kork.web.context.RequestContextProvider
import com.netflix.spinnaker.kork.web.selector.DefaultServiceSelector
import com.netflix.spinnaker.kork.web.selector.SelectableService
import com.netflix.spinnaker.kork.web.selector.ServiceSelector
import com.netflix.spinnaker.okhttp.OkHttp3MetricsInterceptor
import com.netflix.spinnaker.okhttp.OkHttpClientConfigurationProperties
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.web.servlet.FilterRegistrationBean
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Import
import org.springframework.context.annotation.Primary
import org.springframework.core.Ordered
import org.springframework.http.converter.json.AbstractJackson2HttpMessageConverter
import org.springframework.security.web.context.AbstractSecurityWebApplicationInitializer
import org.springframework.session.data.redis.config.ConfigureRedisAction
import org.springframework.session.data.redis.config.annotation.web.http.RedisHttpSessionConfiguration
import org.springframework.util.CollectionUtils
import org.springframework.web.client.RestTemplate
import redis.clients.jedis.JedisPool
import retrofit.Endpoint

import javax.servlet.*
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

import static retrofit.Endpoints.newFixedEndpoint

@CompileStatic
@Configuration
@Slf4j
@EnableConfigurationProperties([FiatClientConfigurationProperties, DynamicRoutingConfigProperties])
@Import([PluginsAutoConfiguration, DeckPluginConfiguration, PluginWebConfiguration])
class GateConfig extends RedisHttpSessionConfiguration {

  private ServiceClientProvider serviceClientProvider

  @Value('${server.session.timeout-in-seconds:3600}')
  void setSessionTimeout(int maxInactiveIntervalInSeconds) {
    super.setMaxInactiveIntervalInSeconds(maxInactiveIntervalInSeconds)
  }

  @Autowired
  void setServiceClientProvider(ServiceClientProvider serviceClientProvider) {
    this.serviceClientProvider = serviceClientProvider
  }

  @Autowired
  GateConfig(@Value('${server.session.timeout-in-seconds:3600}') int maxInactiveIntervalInSeconds) {
    super.setMaxInactiveIntervalInSeconds(maxInactiveIntervalInSeconds)
  }

  /**
   * This pool is used for the rate limit storage, as opposed to the JedisConnectionFactory, which
   * is a separate pool used for Spring Boot's session management.
   */
  @Bean
  JedisPool jedis(@Value('${redis.connection:redis://localhost:6379}') String connection,
                  @Value('${redis.timeout:2000}') int timeout) {
    return new JedisPool(new URI(connection), timeout)
  }

  @Bean
  @ConditionalOnMissingBean(RestTemplate)
  RestTemplate restTemplate() {
    new RestTemplate()
  }

  /**
   * Always disable the ConfigureRedisAction that Spring Boot uses internally. Instead we use one
   * qualified with @ConnectionPostProcessor. See
   * {@link PostConnectionConfiguringJedisConnectionFactory}.
   * */
  @Bean
  @Primary
  ConfigureRedisAction springBootConfigureRedisAction() {
    return ConfigureRedisAction.NO_OP
  }

  @Bean
  @ConnectionPostProcessor
  @ConditionalOnProperty("redis.configuration.secure")
  ConfigureRedisAction connectionPostProcessorConfigureRedisAction() {
    return ConfigureRedisAction.NO_OP
  }

  @Bean
  ExecutorService executorService() {
    Executors.newCachedThreadPool()
  }

  @Autowired
  Registry registry

  @Autowired
  EurekaLookupService eurekaLookupService

  @Autowired
  ServiceConfiguration serviceConfiguration

  /**
   * This needs to be before the yaml converter in order for json to be the default
   * response type.
   */
  @Bean
  AbstractJackson2HttpMessageConverter jsonHttpMessageConverter() {
    ObjectMapper objectMapper = new ObjectMapper()
      .enable(DeserializationFeature.READ_UNKNOWN_ENUM_VALUES_AS_NULL)
      .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
      .registerModule(new JavaTimeModule())

    return new JsonHttpMessageConverter(objectMapper)
  }

  @Bean
  AbstractJackson2HttpMessageConverter yamlHttpMessageConverter() {
    return new YamlHttpMessageConverter(new YAMLMapper())
  }

  @Bean
  RequestContextProvider requestContextProvider() {
    return new AuthenticatedRequestContextProvider();
  }

  @Bean
  OrcaServiceSelector orcaServiceSelector(RequestContextProvider contextProvider) {
    return new OrcaServiceSelector(createClientSelector("orca", OrcaService), contextProvider)
  }

  @Bean
  @Primary
  FiatService fiatService() {
    // always create the fiat service even if 'services.fiat.enabled' is 'false' (it can be enabled dynamically)
    createClient "fiat", FiatService, null, true
  }

  @Bean
  ExtendedFiatService extendedFiatService() {
    // always create the fiat service even if 'services.fiat.enabled' is 'false' (it can be enabled dynamically)
    createClient "fiat", ExtendedFiatService,  null, true
  }

  @Bean
  @ConditionalOnProperty("services.fiat.config.dynamic-endpoints.login")
  FiatService fiatLoginService() {
    // always create the fiat service even if 'services.fiat.enabled' is 'false' (it can be enabled dynamically)
    createClient "fiat", FiatService,  "login", true
  }


  @Bean
  Front50Service front50Service() {
    createClient "front50", Front50Service
  }

  @Bean
  ClouddriverService clouddriverService() {
    createClient "clouddriver", ClouddriverService
  }

  @Bean
  @ConditionalOnProperty("services.keel.enabled")
  KeelService keelService(OkHttpClientProvider clientProvider) {
    createClient "keel", KeelService
  }

  @Bean
  ClouddriverServiceSelector clouddriverServiceSelector(ClouddriverService defaultClouddriverService,
                                                        DynamicConfigService dynamicConfigService,
                                                        RequestContextProvider contextProvider
  ) {
    if (serviceConfiguration.getService("clouddriver").getConfig().containsKey("dynamicEndpoints")) {
      def endpoints = (Map<String, String>) serviceConfiguration.getService("clouddriver").getConfig().get("dynamicEndpoints")
      // translates the following config:
      //   dynamicEndpoints:
      //     deck: url

      // into a SelectableService that would be produced by an equivalent config:
      //   baseUrl: url
      //   config:
      //     selectorClass: com.netflix.spinnaker.kork.web.selector.ByUserOriginSelector
      //     priority: 2
      //     origin: deck

      def defaultSelector = new DefaultServiceSelector(
        defaultClouddriverService,
        1,
        null)

      List<ServiceSelector> selectors = []
      endpoints.each { sourceApp, url ->
        def service = buildService("clouddriver",  ClouddriverService, newFixedEndpoint(url))
        selectors << new ByUserOriginSelector(service, 2, ['origin': (Object) sourceApp])
      }

      return new ClouddriverServiceSelector(
        new SelectableService(selectors + defaultSelector), dynamicConfigService, contextProvider)
    }

    SelectableService selectableService = createClientSelector("clouddriver", ClouddriverService)
    return new ClouddriverServiceSelector(selectableService, dynamicConfigService, contextProvider)
  }

  //---- semi-optional components:
  @Bean
  @ConditionalOnProperty('services.rosco.enabled')
  RoscoService roscoService() {
    createClient "rosco", RoscoService
  }

  @Bean
  @ConditionalOnProperty('services.rosco.enabled')
  RoscoServiceSelector roscoServiceSelector(RoscoService defaultService) {
    return new RoscoServiceSelector(
      createClientSelector("rosco", RoscoService),
      defaultService
    )
  }

  //---- optional backend components:
  @Bean
  @ConditionalOnProperty('services.echo.enabled')
  EchoService echoService() {
    createClient "echo", EchoService
  }

  @Bean
  @ConditionalOnProperty('services.igor.enabled')
  IgorService igorService() {
    createClient "igor", IgorService
  }

  @Bean
  @ConditionalOnProperty('services.mine.enabled')
  MineService mineService() {
    createClient "mine", MineService
  }

  @Bean
  @ConditionalOnProperty("services.keel.enabled")
  KeelService keelService() {
    createClient "keel", KeelService
  }

  @Bean
  @ConditionalOnProperty('services.kayenta.enabled')
  KayentaService kayentaService(OkHttpClientConfigurationProperties props,
                                OkHttp3MetricsInterceptor interceptor,
                                @Value('${services.kayenta.externalhttps:false}') boolean kayentaExternalHttps) {
    if (kayentaExternalHttps) {
      def noSslCustomizationProps = props.clone()
      noSslCustomizationProps.keyStore = null
      noSslCustomizationProps.trustStore = null
      def okHttpClient = new OkHttp3ClientConfiguration(noSslCustomizationProps, interceptor).create().build()
      createClient "kayenta", KayentaService
    } else {
      createClient "kayenta", KayentaService
    }
  }

  @Bean
  @ConditionalOnProperty('services.swabbie.enabled')
  SwabbieService swabbieService() {
    createClient("swabbie", SwabbieService)
  }

  private <T> T createClient(String serviceName,
                             Class<T> type,
                             String dynamicName = null,
                             boolean forceEnabled = false) {
    Service service = serviceConfiguration.getService(serviceName)
    if (service == null) {
      throw new IllegalArgumentException("Unknown service ${serviceName} requested of type ${type}")
    }

    if (!service.enabled && !forceEnabled) {
      return null
    }

    Endpoint endpoint = serviceConfiguration.getServiceEndpoint(serviceName, dynamicName)

    buildService(serviceName, type, endpoint)
  }

  private <T> T buildService(String serviceName, Class<T> type, Endpoint endpoint) {
    // New role providers break deserialization if this is not enabled.
    ObjectMapper objectMapper = new ObjectMapper()
      .enable(DeserializationFeature.READ_UNKNOWN_ENUM_VALUES_AS_NULL)
      .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
      .registerModule(new JavaTimeModule())

    serviceClientProvider.getService(type, new DefaultServiceEndpoint(serviceName, endpoint.url), objectMapper)

  }

  private <T> SelectableService createClientSelector(String serviceName, Class<T> type) {
    Service service = serviceConfiguration.getService(serviceName)
    if (CollectionUtils.isEmpty(service?.getBaseUrls())) {
      throw new IllegalArgumentException("Unknown service ${serviceName} requested of type ${type}")
    }

    return new SelectableService(
      service.getBaseUrls().collect {
        def selector = new DefaultServiceSelector(
          buildService(
            serviceName,
            type,
            newFixedEndpoint(it.baseUrl)),
          it.priority,
          it.config)

        def selectorClass = it.config?.selectorClass as Class<ServiceSelector>
        if (selectorClass) {
          log.debug("Initializing selector class {} with baseUrl={}, priority={}, config={}", selectorClass, it.baseUrl, it.priority, it.config)
          selector = selectorClass.getConstructors()[0].newInstance(
            selector.service, it.priority, it.config
          )
        }
        selector
      } as List<ServiceSelector>
    )
  }

  @Bean
  FilterRegistrationBean resetAuthenticatedRequestFilter() {
    def frb = new FilterRegistrationBean(new ResetAuthenticatedRequestFilter())
    frb.order = Ordered.HIGHEST_PRECEDENCE
    return frb
  }

  /**
   * This AuthenticatedRequestFilter pulls the email and accounts out of the Spring
   * security context in order to enabling forwarding them to downstream components.
   *
   * Additionally forwards request origin metadata (deck vs api).
   */
  @Bean
  FilterRegistrationBean authenticatedRequestFilter() {
    // no need to force the `AuthenticatedRequestFilter` to create a request id as that is
    // handled by the `RequestTimingFilter`.
    def frb = new FilterRegistrationBean(new AuthenticatedRequestFilter(false, true, false, false))
    frb.order = Ordered.LOWEST_PRECEDENCE - 1
    return frb
  }

  /**
   * This pulls the `springSecurityFilterChain` in front of the {@link AuthenticatedRequestFilter},
   * because the user must be authenticated through the security filter chain before their username/credentials
   * can be pulled and forwarded in the AuthenticatedRequestFilter.
   */
  @Bean
  FilterRegistrationBean securityFilterChain(
    @Qualifier(AbstractSecurityWebApplicationInitializer.DEFAULT_FILTER_NAME) Filter securityFilter) {
    def frb = new FilterRegistrationBean(securityFilter)
    frb.order = 0
    frb.name = AbstractSecurityWebApplicationInitializer.DEFAULT_FILTER_NAME
    return frb
  }

  @Bean
  @ConditionalOnProperty("request-logging.enabled")
  FilterRegistrationBean requestLoggingFilter() {
    def frb = new FilterRegistrationBean(new RequestLoggingFilter())
    // this filter should be placed very early in the request chain to ensure we track an accurate start time and
    // have a request id available to propagate across thread and service boundaries.
    frb.order = Ordered.HIGHEST_PRECEDENCE + 1
    return frb
  }

  @Bean
  FilterRegistrationBean requestSheddingFilter(DynamicConfigService dynamicConfigService) {
    def frb = new FilterRegistrationBean(new RequestSheddingFilter(dynamicConfigService, registry))

    /*
     * This filter should:
     * - be placed early in the request chain to allow for requests to be shed prior to the security filter chain.
     * - be placed after the RequestLoggingFilter such that shed requests are logged.
     */
    frb.order = Ordered.HIGHEST_PRECEDENCE + 2
    return frb
  }

  @Bean
  FiatStatus fiatStatus(DynamicConfigService dynamicConfigService,
                        Registry registry,
                        FiatClientConfigurationProperties fiatClientConfigurationProperties) {
    return new FiatStatus(registry, dynamicConfigService, fiatClientConfigurationProperties)
  }

  @Bean
  FiatPermissionEvaluator fiatPermissionEvaluator(FiatStatus fiatStatus,
                                                  Registry registry,
                                                  FiatService fiatService,
                                                  FiatClientConfigurationProperties fiatClientConfigurationProperties) {
    return new FiatPermissionEvaluator(registry, fiatService, fiatClientConfigurationProperties, fiatStatus)
  }
}
