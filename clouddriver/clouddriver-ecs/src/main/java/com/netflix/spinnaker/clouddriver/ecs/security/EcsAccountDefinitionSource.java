package com.netflix.spinnaker.clouddriver.ecs.security;

import com.netflix.spinnaker.clouddriver.security.AccountDefinitionRepository;
import com.netflix.spinnaker.clouddriver.security.AccountDefinitionSource;
import com.netflix.spinnaker.credentials.definition.CredentialsDefinitionSource;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;
import java.util.Optional;

@Configuration
@ConditionalOnProperty({"account.storage.enabled", "account.storage.aws.enabled", "account.storage.ecs.enabled"})
public class EcsAccountDefinitionSource {

  @Bean
  CredentialsDefinitionSource<ECSCredentialsConfig.Account> ecsAccountSource(
    AccountDefinitionRepository repository,
    Optional<List<CredentialsDefinitionSource<ECSCredentialsConfig.Account>>> additionalSources,
    ECSCredentialsConfig ecsCredentialsConfig) {
    return new AccountDefinitionSource<>(
      repository,
      ECSCredentialsConfig.Account.class,
      additionalSources.orElseGet(() -> List.of(ecsCredentialsConfig::getAccounts)));
  }
}
