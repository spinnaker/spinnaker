package com.netflix.spinnaker.config

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper
import com.netflix.spectator.api.Registry
import com.netflix.spinnaker.filters.AuthenticatedRequestFilter
import com.netflix.spinnaker.keel.api.plugins.PostDeployActionHandler
import com.netflix.spinnaker.keel.api.plugins.ResourceHandler
import com.netflix.spinnaker.keel.api.plugins.VerificationEvaluator
import com.netflix.spinnaker.keel.api.support.ExtensionRegistry
import com.netflix.spinnaker.keel.resources.SpecMigrator
import com.netflix.spinnaker.keel.rest.DeliveryConfigYamlParsingFilter
import com.netflix.spinnaker.keel.schema.Generator
import com.netflix.spinnaker.keel.schema.ResourceKindSchemaCustomizer
import com.netflix.spinnaker.keel.schema.TagVersionStrategySchemaCustomizer
import com.netflix.spinnaker.keel.serialization.configuredObjectMapper
import com.netflix.spinnaker.keel.serialization.configuredYamlMapper
import com.netflix.spinnaker.kork.web.interceptors.MetricsInterceptor
import de.huxhorn.sulky.ulid.ULID
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.jackson.JsonComponentModule
import org.springframework.boot.task.TaskSchedulerCustomizer
import org.springframework.boot.web.servlet.FilterRegistrationBean
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Primary
import org.springframework.core.Ordered.HIGHEST_PRECEDENCE
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.web.SecurityFilterChain
import org.springframework.web.servlet.config.annotation.InterceptorRegistry
import org.springframework.web.servlet.config.annotation.PathMatchConfigurer
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer
import org.springframework.web.util.pattern.PathPatternParser
import java.time.Clock

private const val IPC_SERVER_METRIC = "controller.invocations"

@Configuration
class DefaultConfiguration(
  val spectatorRegistry: Registry
) : WebMvcConfigurer {
  override fun configurePathMatch(configurer: PathMatchConfigurer) {
    // Keep PathPatternParser but allow an optional trailing “/”
    val parser = PathPatternParser().apply {
      isMatchOptionalTrailingSeparator = true   //  ⇦ key line
    }
    configurer.patternParser = parser
  }
  /**
   * Enable controller metrics
   */
  override fun addInterceptors(interceptorRegistry: InterceptorRegistry) {

    /**
     * Path variables that will be added as tags to the metrics
     *
     * Lorin chose these particular variables arbitrarily as potentially useful. Once we start consuming
     * these metrics, we should revisit whether to change these based on usefulness & cardinality
     */
    val pathVarsToTag = listOf("application")

    val queryParamsToTag = null

    // exclude list copied from fiat: https://github.com/spinnaker/fiat/blob/0d58386152ad78234b2554f5efdf12d26b77d57c/fiat-web/src/main/java/com/netflix/spinnaker/fiat/config/FiatConfig.java#L50
    val exclude = listOf("BasicErrorController")

    val interceptor = MetricsInterceptor(spectatorRegistry, IPC_SERVER_METRIC, pathVarsToTag, queryParamsToTag, exclude)
    interceptorRegistry.addInterceptor(interceptor)
  }

  @Bean
  fun noSecurityFilterChain(http: HttpSecurity): SecurityFilterChain {
    http
      .authorizeHttpRequests { it.anyRequest().permitAll() }
      .csrf { it.disable() }
    return http.build()
  }

  @Bean
  @ConditionalOnMissingBean
  fun clock(): Clock = Clock.systemUTC()

  @Bean
  fun idGenerator(): ULID = ULID()

  @Bean
  fun jsonComponentModule() = JsonComponentModule()

  @Bean(name = ["jsonMapper", "objectMapper"])
  @Primary
  fun objectMapper(jsonComponentModule: JsonComponentModule): ObjectMapper =
    configuredObjectMapper().registerModule(jsonComponentModule)

  @Bean(name = ["yamlMapper"])
  fun yamlMapper(jsonComponentModule: JsonComponentModule): YAMLMapper =
    configuredYamlMapper().registerModule(jsonComponentModule) as YAMLMapper

  @Bean
  @ConditionalOnMissingBean(ResourceHandler::class)
  fun noResourcePlugins(): List<ResourceHandler<*, *>> = emptyList()

  @Bean
  @ConditionalOnMissingBean(VerificationEvaluator::class)
  fun noVerificationEvaluators(): List<VerificationEvaluator<*>> = emptyList()

  @Bean
  @ConditionalOnMissingBean(PostDeployActionHandler::class)
  fun noPostDeployActionRunners(): List<PostDeployActionHandler<*>> = emptyList()

  @Bean
  fun authenticatedRequestFilter(): FilterRegistrationBean<AuthenticatedRequestFilter> =
    FilterRegistrationBean(AuthenticatedRequestFilter(true))
      .apply { order = HIGHEST_PRECEDENCE }

  @Bean
  fun taskSchedulerCustomizer(@Value("\${keel.scheduler.pool-size:10}") poolSize: Int): TaskSchedulerCustomizer =
    TaskSchedulerCustomizer { scheduler ->
      scheduler.poolSize = poolSize
      scheduler.threadNamePrefix = "scheduler-"
    }

  @Bean
  fun deliveryConfigYamlParsingFilter(): FilterRegistrationBean<*> {
    val registration = FilterRegistrationBean<DeliveryConfigYamlParsingFilter>()
    registration.filter = DeliveryConfigYamlParsingFilter()
    registration.setName("deliveryConfigYamlParsingFilter")
    registration.addUrlPatterns("/delivery-configs/validate")
    registration.order = 10
    return registration
  }

  @Bean
  fun schemaGenerator(
    extensionRegistry: ExtensionRegistry,
    resourceHandlers: List<ResourceHandler<*, *>>,
    migrators: List<SpecMigrator<*, *>>
  ): Generator = Generator(
    extensionRegistry = extensionRegistry,
    schemaCustomizers = listOf(
      ResourceKindSchemaCustomizer(
        resourceHandlers.map { it.supportedKind.kind } + migrators.map { it.input.kind }
      ),
      TagVersionStrategySchemaCustomizer
    ),
    options = Generator.Options(
      lowerCaseEnums = true
    )
  )
}
