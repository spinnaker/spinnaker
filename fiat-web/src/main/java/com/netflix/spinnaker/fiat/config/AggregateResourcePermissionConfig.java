package com.netflix.spinnaker.fiat.config;

import com.netflix.spinnaker.fiat.model.resources.Account;
import com.netflix.spinnaker.fiat.model.resources.Application;
import com.netflix.spinnaker.fiat.model.resources.BuildService;
import com.netflix.spinnaker.fiat.providers.AggregatingResourcePermissionProvider;
import com.netflix.spinnaker.fiat.providers.ResourcePermissionProvider;
import com.netflix.spinnaker.fiat.providers.ResourcePermissionSource;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AggregateResourcePermissionConfig {

  @Bean
  @ConditionalOnProperty(value = "auth.permissions.provider.account", havingValue = "aggregate")
  public ResourcePermissionProvider<Account> aggregateAccountPermissionProvider(
      List<ResourcePermissionSource<Account>> sources) {
    return new AggregatingResourcePermissionProvider<>(sources);
  }

  @Bean
  @ConditionalOnProperty(value = "auth.permissions.provider.application", havingValue = "aggregate")
  public ResourcePermissionProvider<Application> aggregateApplicationPermissionProvider(
      List<ResourcePermissionSource<Application>> sources) {
    return new AggregatingResourcePermissionProvider<>(sources);
  }

  @Bean
  @ConditionalOnProperty(
      value = "auth.permissions.provider.build-service",
      havingValue = "aggregate")
  public ResourcePermissionProvider<BuildService> aggregateBuildServicePermissionProvider(
      List<ResourcePermissionSource<BuildService>> sources) {
    return new AggregatingResourcePermissionProvider<>(sources);
  }
}
