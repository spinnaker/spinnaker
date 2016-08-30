package com.netflix.spinnaker.fiat.config;

import com.netflix.spinnaker.fiat.permissions.InMemoryPermissionsRepository;
import com.netflix.spinnaker.fiat.permissions.PermissionsRepository;
import com.netflix.spinnaker.fiat.roles.UserRolesProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

import java.util.*;

@Configuration
@Import(RetrofitConfig.class)
public class FiatConfig {

  @Bean
  @ConditionalOnMissingBean(PermissionsRepository.class)
  PermissionsRepository permissionsRepository() {
    return new InMemoryPermissionsRepository();
  }

  @Bean
  @ConditionalOnMissingBean(UserRolesProvider.class)
  UserRolesProvider defaultUserRolesProvider() {
    return new UserRolesProvider() {
      @Override
      public Map<String, Collection<String>> multiLoadRoles(Collection<String> userIds) {
        return new HashMap<>();
      }

      @Override
      public List<String> loadRoles(String userId) {
        return new ArrayList<>();
      }
    };
  }
}
