package com.netflix.spinnaker.fiat.config;

import com.google.common.collect.ImmutableList;
import com.netflix.spectator.api.Registry;
import com.netflix.spinnaker.config.PluginsAutoConfiguration;
import com.netflix.spinnaker.fiat.model.Authorization;
import com.netflix.spinnaker.fiat.model.resources.Application;
import com.netflix.spinnaker.fiat.model.resources.Role;
import com.netflix.spinnaker.fiat.permissions.DefaultFallbackPermissionsResolver;
import com.netflix.spinnaker.fiat.permissions.ExternalUser;
import com.netflix.spinnaker.fiat.permissions.FallbackPermissionsResolver;
import com.netflix.spinnaker.fiat.providers.DefaultApplicationResourceProvider;
import com.netflix.spinnaker.fiat.providers.DefaultServiceAccountPredicateProvider;
import com.netflix.spinnaker.fiat.providers.DefaultServiceAccountResourceProvider;
import com.netflix.spinnaker.fiat.providers.ResourcePermissionProvider;
import com.netflix.spinnaker.fiat.providers.ServiceAccountPredicateProvider;
import com.netflix.spinnaker.fiat.providers.internal.ClouddriverService;
import com.netflix.spinnaker.fiat.providers.internal.Front50Service;
import com.netflix.spinnaker.fiat.roles.UserRolesProvider;
import com.netflix.spinnaker.filters.AuthenticatedRequestFilter;
import com.netflix.spinnaker.kork.web.interceptors.MetricsInterceptor;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.val;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.core.Ordered;
import org.springframework.http.MediaType;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.web.servlet.config.annotation.ContentNegotiationConfigurer;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurerAdapter;

@Configuration
@Import({RetrofitConfig.class, PluginsAutoConfiguration.class})
@EnableConfigurationProperties(FiatServerConfigurationProperties.class)
public class FiatConfig extends WebMvcConfigurerAdapter {

  @Autowired private Registry registry;

  @Override
  public void addInterceptors(InterceptorRegistry registry) {
    var pathVarsToTag = ImmutableList.of("accountName", "applicationName", "resourceName");
    List<String> exclude = ImmutableList.of("BasicErrorController");
    MetricsInterceptor interceptor =
        new MetricsInterceptor(this.registry, "controller.invocations", pathVarsToTag, exclude);
    registry.addInterceptor(interceptor);
  }

  @Override
  public void configureContentNegotiation(ContentNegotiationConfigurer configurer) {
    super.configureContentNegotiation(configurer);
    configurer.favorPathExtension(false).defaultContentType(MediaType.APPLICATION_JSON);
  }

  @Bean
  @ConditionalOnMissingBean(UserRolesProvider.class)
  UserRolesProvider defaultUserRolesProvider() {
    return new UserRolesProvider() {
      @Override
      public Map<String, Collection<Role>> multiLoadRoles(Collection<ExternalUser> users) {
        return new HashMap<>();
      }

      @Override
      public List<Role> loadRoles(ExternalUser user) {
        return new ArrayList<>();
      }
    };
  }

  @Bean
  DefaultApplicationResourceProvider applicationProvider(
      Front50Service front50Service,
      ClouddriverService clouddriverService,
      ResourcePermissionProvider<Application> permissionProvider,
      FallbackPermissionsResolver executeFallbackPermissionsResolver,
      FiatServerConfigurationProperties properties) {
    return new DefaultApplicationResourceProvider(
        front50Service,
        clouddriverService,
        permissionProvider,
        executeFallbackPermissionsResolver,
        properties.isAllowAccessToUnknownApplications());
  }

  @Bean
  @ConditionalOnProperty(
      value = "fiat.service-account-resource-provider.default.enabled",
      matchIfMissing = true)
  DefaultServiceAccountResourceProvider serviceAccountResourceProvider(
      Front50Service front50Service,
      Collection<ServiceAccountPredicateProvider> serviceAccountPredicateProviders) {
    return new DefaultServiceAccountResourceProvider(
        front50Service, serviceAccountPredicateProviders);
  }

  @Bean
  DefaultFallbackPermissionsResolver executeFallbackPermissionsResolver(
      FiatServerConfigurationProperties properties) {
    return new DefaultFallbackPermissionsResolver(
        Authorization.EXECUTE, properties.getExecuteFallback());
  }

  @Bean
  public DefaultServiceAccountPredicateProvider defaultServiceAccountPredicateProvider(
      FiatRoleConfig fiatRoleConfig) {
    return new DefaultServiceAccountPredicateProvider(fiatRoleConfig);
  }

  /**
   * This AuthenticatedRequestFilter pulls the email and accounts out of the Spring security context
   * in order to enabling forwarding them to downstream components.
   */
  @Bean
  FilterRegistrationBean authenticatedRequestFilter() {
    val frb = new FilterRegistrationBean(new AuthenticatedRequestFilter(true));
    frb.setOrder(Ordered.LOWEST_PRECEDENCE);
    return frb;
  }

  @Bean
  public TaskScheduler taskScheduler(@Value("${fiat.scheduler.pool-size:5}") int poolSize) {
    ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
    scheduler.setThreadNamePrefix("scheduler-");
    scheduler.setPoolSize(poolSize);

    return scheduler;
  }
}
