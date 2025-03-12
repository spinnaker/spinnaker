package com.netflix.spinnaker.config;

import com.netflix.spinnaker.clouddriver.docker.registry.config.DockerRegistryConfigurationProperties;
import com.netflix.spinnaker.clouddriver.docker.registry.config.DockerRegistryConfigurationProperties.ManagedAccount;
import com.netflix.spinnaker.clouddriver.security.AccountDefinitionRepository;
import com.netflix.spinnaker.clouddriver.security.AccountDefinitionSource;
import com.netflix.spinnaker.credentials.definition.CredentialsDefinitionSource;
import java.util.List;
import java.util.Optional;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

@Configuration
@ConditionalOnProperty({"account.storage.enabled", "account.storage.docker-registry.enabled"})
public class DockerRegistryAccountDefinitionSourceConfiguration {
  @Bean
  @Primary
  public CredentialsDefinitionSource<ManagedAccount> dockerRegistryAccountSource(
      AccountDefinitionRepository repository,
      Optional<List<CredentialsDefinitionSource<ManagedAccount>>> additionalSources,
      DockerRegistryConfigurationProperties properties) {
    return new AccountDefinitionSource<>(
        repository,
        ManagedAccount.class,
        additionalSources.orElseGet(() -> List.of(properties::getAccounts)));
  }
}
