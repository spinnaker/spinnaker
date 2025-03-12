/*
 * Copyright 2021 Apple Inc.
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

package com.netflix.spinnaker.clouddriver.config;

import com.fasterxml.jackson.annotation.JsonTypeName;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.jsontype.NamedType;
import com.netflix.spinnaker.clouddriver.jackson.AccountDefinitionModule;
import com.netflix.spinnaker.clouddriver.security.AccountCredentialsProvider;
import com.netflix.spinnaker.clouddriver.security.AccountDefinitionMapper;
import com.netflix.spinnaker.clouddriver.security.AccountDefinitionRepository;
import com.netflix.spinnaker.clouddriver.security.AccountDefinitionSecretManager;
import com.netflix.spinnaker.clouddriver.security.AccountDefinitionService;
import com.netflix.spinnaker.clouddriver.security.AccountDefinitionTypeProvider;
import com.netflix.spinnaker.clouddriver.security.AccountDefinitionTypes;
import com.netflix.spinnaker.clouddriver.security.AccountSecurityPolicy;
import com.netflix.spinnaker.clouddriver.security.AllowAllAccountSecurityPolicy;
import com.netflix.spinnaker.clouddriver.security.AuthorizedRolesExtractor;
import com.netflix.spinnaker.clouddriver.security.DefaultAccountSecurityPolicy;
import com.netflix.spinnaker.credentials.definition.CredentialsDefinition;
import com.netflix.spinnaker.fiat.shared.FiatPermissionEvaluator;
import com.netflix.spinnaker.kork.secrets.SecretManager;
import com.netflix.spinnaker.kork.secrets.SecretSession;
import com.netflix.spinnaker.kork.secrets.user.UserSecretManager;
import com.netflix.spinnaker.kork.secrets.user.UserSecretReference;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ResourceLoader;
import org.springframework.core.type.filter.AssignableTypeFilter;
import org.springframework.util.ClassUtils;

/**
 * Provides configuration settings related to managing account credential definitions at runtime.
 *
 * @see Properties
 */
@Configuration
@EnableConfigurationProperties(AccountDefinitionConfiguration.Properties.class)
@Log4j2
@RequiredArgsConstructor
public class AccountDefinitionConfiguration {

  private final Properties properties;

  @Bean
  @ConditionalOnMissingBean
  public AccountSecurityPolicy accountSecurity(
      @Nullable FiatPermissionEvaluator permissionEvaluator,
      @Value("${services.fiat.enabled:false}") boolean fiatEnabled) {
    return fiatEnabled && permissionEvaluator != null
        ? new DefaultAccountSecurityPolicy(permissionEvaluator)
        : new AllowAllAccountSecurityPolicy();
  }

  @Bean
  public AccountDefinitionSecretManager accountDefinitionSecretManager(
      UserSecretManager userSecretManager, AccountSecurityPolicy policy) {
    return new AccountDefinitionSecretManager(userSecretManager, policy);
  }

  /**
   * Creates a mapper that can convert between JSON and {@link CredentialsDefinition} classes that
   * are annotated with {@link JsonTypeName}. Account definition classes are scanned in {@code
   * com.netflix.spinnaker.clouddriver} and any additional packages configured in {@link
   * Properties#setAdditionalScanPackages(List)}. Only eligible account definition classes are used
   * with an ObjectMapper to first convert any referenced {@link UserSecretReference} URIs and then
   * convert to an appropriate CredentialsDefinition instance.
   *
   * @see com.netflix.spinnaker.kork.jackson.ObjectMapperSubtypeConfigurer
   */
  @Bean
  public AccountDefinitionMapper accountDefinitionMapper(
      ObjectMapper mapper,
      AccountDefinitionSecretManager accountDefinitionSecretManager,
      SecretManager secretManager) {
    return new AccountDefinitionMapper(
        mapper, accountDefinitionSecretManager, new SecretSession(secretManager));
  }

  @Bean
  @ConditionalOnBean(AccountDefinitionRepository.class)
  public AccountDefinitionService accountDefinitionService(
      AccountDefinitionRepository repository,
      AccountDefinitionSecretManager secretManager,
      AccountCredentialsProvider provider,
      AccountSecurityPolicy security,
      List<AuthorizedRolesExtractor> extractors) {
    return new AccountDefinitionService(repository, secretManager, provider, security, extractors);
  }

  @Bean
  public AccountDefinitionModule accountDefinitionModule(
      List<AccountDefinitionTypeProvider> typeProviders) {
    return new AccountDefinitionModule(
        typeProviders.stream()
            .flatMap(
                provider ->
                    provider.getCredentialsTypes().entrySet().stream()
                        .map(e -> new NamedType(e.getValue(), e.getKey())))
            .toArray(NamedType[]::new));
  }

  /**
   * Exports all discovered account definition types from scanning the classpath. Plugins may
   * register additional provider beans to register additional account types to support in {@link
   * AccountDefinitionRepository}.
   */
  @Bean
  public AccountDefinitionTypeProvider defaultAccountDefinitionTypeProvider(ResourceLoader loader) {
    var provider = new ClassPathScanningCandidateComponentProvider(false);
    provider.setResourceLoader(loader);
    provider.addIncludeFilter(new AssignableTypeFilter(CredentialsDefinition.class));
    List<String> scanPackages = new ArrayList<>(properties.additionalScanPackages);
    scanPackages.add(0, "com.netflix.spinnaker.clouddriver");
    return () ->
        scanPackages.stream()
            .flatMap(packageName -> provider.findCandidateComponents(packageName).stream())
            .map(BeanDefinition::getBeanClassName)
            .filter(Objects::nonNull)
            .map(className -> tryLoadAccountDefinitionClassName(className, loader.getClassLoader()))
            .filter(Objects::nonNull)
            .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
  }

  private Map.Entry<String, Class<? extends CredentialsDefinition>>
      tryLoadAccountDefinitionClassName(String className, ClassLoader classLoader) {
    try {
      Class<? extends CredentialsDefinition> subtype =
          ClassUtils.forName(className, classLoader).asSubclass(CredentialsDefinition.class);
      String typeName = AccountDefinitionTypes.getCredentialsTypeName(subtype);
      if (typeName != null) {
        log.info("Discovered credentials definition type '{}' from class '{}'", typeName, subtype);
        return Map.entry(typeName, subtype);
      } else {
        log.debug(
            "Skipping CredentialsDefinition class '{}' as it does not define a @CredentialsType annotation",
            subtype);
      }
    } catch (ClassNotFoundException e) {
      log.warn(
          "Unable to load CredentialsDefinition class '{}'. Credentials with this type will not be loaded.",
          className,
          e);
    }
    return null;
  }

  @ConfigurationProperties("account.storage")
  @ConditionalOnProperty("account.storage.enabled")
  @Data
  public static class Properties {
    /**
     * Indicates whether to enable durable storage for account definitions. When enabled with an
     * implementation of {@link AccountDefinitionRepository}, account definitions can be stored and
     * retrieved by a durable storage provider.
     */
    private boolean enabled;

    /**
     * Additional packages to scan for {@link
     * com.netflix.spinnaker.credentials.definition.CredentialsDefinition} implementation classes
     * that may be annotated with {@link JsonTypeName} to participate in the account management
     * system. These packages are in addition to the default scan package from within Clouddriver.
     * Note that this configuration option only works for account types that are compiled in
     * Spinnaker; plugin account types must register a {@link AccountDefinitionTypeProvider} bean
     * for additional types.
     */
    private List<String> additionalScanPackages = List.of();

    // TODO(jvz): accounts pubsub config for https://github.com/spinnaker/kork/pull/958
    //  - @Import(PubsubConfig.class)
    //  - CredentialsDefinitionNotifier bean
    //  - AccountDefinitionProcessor bean
    //  - account.storage.topic property for accounts pubsub topic
    //  - @CredentialsType javadoc
  }
}
