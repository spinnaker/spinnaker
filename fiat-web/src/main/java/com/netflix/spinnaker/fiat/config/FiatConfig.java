package com.netflix.spinnaker.fiat.config;

import com.google.common.collect.ImmutableList;
import com.netflix.spectator.api.Registry;
import com.netflix.spinnaker.fiat.model.resources.Role;
import com.netflix.spinnaker.fiat.permissions.ExternalUser;
import com.netflix.spinnaker.fiat.providers.DefaultApplicationProvider;
import com.netflix.spinnaker.fiat.providers.internal.ClouddriverService;
import com.netflix.spinnaker.fiat.providers.internal.Front50Service;
import com.netflix.spinnaker.fiat.roles.UserRolesProvider;
import com.netflix.spinnaker.filters.AuthenticatedRequestFilter;
import com.netflix.spinnaker.kork.web.interceptors.MetricsInterceptor;
import lombok.val;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.core.Ordered;
import org.springframework.http.MediaType;
import org.springframework.web.servlet.config.annotation.ContentNegotiationConfigurer;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurerAdapter;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Configuration
@Import(RetrofitConfig.class)
@EnableConfigurationProperties(FiatServerConfigurationProperties.class)
public class FiatConfig extends WebMvcConfigurerAdapter {

  @Autowired
  private Registry registry;

  @Override
  public void addInterceptors(InterceptorRegistry registry) {
    List<String> pathVarsToTag = ImmutableList.of("accountName", "applicationName", "resourceName");
    List<String> exclude = ImmutableList.of("BasicErrorController");
    MetricsInterceptor interceptor = new MetricsInterceptor(this.registry,
                                                            "controller.invocations",
                                                            pathVarsToTag,
                                                            exclude);
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
  DefaultApplicationProvider applicationProvider(Front50Service front50Service,
                                                 ClouddriverService clouddriverService,
                                                 FiatServerConfigurationProperties properties) {
    return new DefaultApplicationProvider(
        front50Service,
        clouddriverService,
        properties.isAllowAccessToUnknownApplications()
    );
  }

  /**
   * This AuthenticatedRequestFilter pulls the email and accounts out of the Spring
   * security context in order to enabling forwarding them to downstream components.
   */
  @Bean
  FilterRegistrationBean authenticatedRequestFilter() {
    val frb = new FilterRegistrationBean(new AuthenticatedRequestFilter(true));
    frb.setOrder(Ordered.LOWEST_PRECEDENCE);
    return frb;
  }
}
