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
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.deser.std.StringDeserializer;
import com.netflix.spinnaker.clouddriver.jackson.AccountDefinitionModule;
import com.netflix.spinnaker.clouddriver.security.AccountCredentialsProvider;
import com.netflix.spinnaker.clouddriver.security.AccountDefinitionAuthorizer;
import com.netflix.spinnaker.clouddriver.security.AccountDefinitionMapper;
import com.netflix.spinnaker.clouddriver.security.AccountDefinitionRepository;
import com.netflix.spinnaker.clouddriver.security.AccountDefinitionSecretManager;
import com.netflix.spinnaker.clouddriver.security.AccountDefinitionService;
import com.netflix.spinnaker.credentials.definition.CredentialsDefinition;
import com.netflix.spinnaker.fiat.shared.FiatPermissionEvaluator;
import com.netflix.spinnaker.kork.secrets.EncryptedSecret;
import com.netflix.spinnaker.kork.secrets.SecretManager;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import javax.annotation.Nullable;
import lombok.Data;
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
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.util.ClassUtils;

/**
 * Provides configuration settings related to managing account credential definitions at runtime.
 *
 * @see Properties
 */
@Configuration
@EnableConfigurationProperties(AccountDefinitionConfiguration.Properties.class)
@Log4j2
public class AccountDefinitionConfiguration {

  @Bean
  @ConditionalOnMissingBean
  public AccountDefinitionAuthorizer accountDefinitionAuthorizer(
      Optional<FiatPermissionEvaluator> maybePermissionEvaluator,
      @Value("${services.fiat.enabled:false}") boolean isFiatEnabled) {
    return new AccountDefinitionAuthorizer(
        maybePermissionEvaluator.filter(ignored -> isFiatEnabled).orElse(null));
  }

  @Bean
  public AccountDefinitionSecretManager accountDefinitionSecretManager(
      SecretManager secretManager, AccountDefinitionAuthorizer authorizer) {
    return new AccountDefinitionSecretManager(secretManager, authorizer);
  }

  /**
   * Creates a mapper that can convert between JSON and {@link CredentialsDefinition} classes that
   * are annotated with {@link JsonTypeName}. Account definition classes are scanned in {@code
   * com.netflix.spinnaker.clouddriver} and any additional packages configured in {@link
   * Properties#setAdditionalScanPackages(List)}. Only eligible account definition classes are used
   * with a fresh {@link com.fasterxml.jackson.databind.ObjectMapper} configured with a custom
   * string deserializer to transparently load and decrypt {@link EncryptedSecret} strings. These
   * encrypted secrets must reference a configured {@link
   * com.netflix.spinnaker.kork.secrets.SecretEngine}.
   *
   * @see com.netflix.spinnaker.kork.jackson.ObjectMapperSubtypeConfigurer
   */
  @Bean
  public AccountDefinitionMapper accountDefinitionMapper(
      Jackson2ObjectMapperBuilder mapperBuilder, AccountDefinitionSecretManager secretManager) {
    mapperBuilder.deserializers(createSecretDecryptingDeserializer(secretManager));
    var mapper = mapperBuilder.build();
    return new AccountDefinitionMapper(mapper);
  }

  @Bean
  @ConditionalOnBean(AccountDefinitionRepository.class)
  public AccountDefinitionService accountDefinitionService(
      AccountDefinitionRepository repository,
      AccountDefinitionAuthorizer authorizer,
      AccountCredentialsProvider provider,
      ObjectMapper objectMapper) {
    return new AccountDefinitionService(repository, authorizer, provider, objectMapper);
  }

  @Bean
  public AccountDefinitionModule accountDefinitionModule(
      ResourceLoader loader, Properties properties) {
    return new AccountDefinitionModule(findAccountDefinitionTypes(loader, properties));
  }

  private static StringDeserializer createSecretDecryptingDeserializer(
      AccountDefinitionSecretManager secretManager) {
    return new StringDeserializer() {
      @Override
      public String deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
        var string = super.deserialize(p, ctxt);
        if (EncryptedSecret.isEncryptedSecret(string)) {
          return secretManager.getEncryptedSecret(string);
        } else {
          return string;
        }
      }
    };
  }

  @SuppressWarnings("unchecked")
  private static Class<? extends CredentialsDefinition>[] findAccountDefinitionTypes(
      ResourceLoader loader, Properties properties) {
    var provider = new ClassPathScanningCandidateComponentProvider(false);
    provider.setResourceLoader(loader);
    provider.addIncludeFilter(new AssignableTypeFilter(CredentialsDefinition.class));
    List<String> scanPackages = new ArrayList<>(properties.additionalScanPackages);
    scanPackages.add(0, "com.netflix.spinnaker.clouddriver");
    return scanPackages.stream()
        .flatMap(packageName -> provider.findCandidateComponents(packageName).stream())
        .map(BeanDefinition::getBeanClassName)
        .filter(Objects::nonNull)
        .map(AccountDefinitionConfiguration::loadCredentialsDefinitionType)
        .filter(Objects::nonNull)
        .filter(type -> type.isAnnotationPresent(JsonTypeName.class))
        .peek(
            type ->
                log.info(
                    "Discovered CredentialsDefinition class '{}' with type discriminator '{}'.",
                    type.getName(),
                    type.getAnnotation(JsonTypeName.class).value()))
        .toArray(Class[]::new);
  }

  @Nullable
  private static Class<? extends CredentialsDefinition> loadCredentialsDefinitionType(
      String className) {
    try {
      return ClassUtils.forName(className, null).asSubclass(CredentialsDefinition.class);
    } catch (ClassNotFoundException e) {
      log.warn(
          "Unable to load CredentialsDefinition class '{}'. Credentials with this type will not be loaded.",
          className,
          e);
      return null;
    }
  }

  @ConfigurationProperties("account.storage")
  @ConditionalOnProperty("account.storage.enabled")
  @Data
  public static class Properties {
    /**
     * Indicates whether to enable durable storage for account definitions. When enabled with an
     * implementation of {@link
     * com.netflix.spinnaker.clouddriver.security.AccountDefinitionRepository}, account definitions
     * can be stored and retrieved by a durable storage provider.
     */
    private boolean enabled;

    /**
     * Additional packages to scan for {@link
     * com.netflix.spinnaker.credentials.definition.CredentialsDefinition} implementation classes
     * that may be annotated with {@link JsonTypeName} to participate in the account management
     * system. These packages are in addition to the default scan package from within Clouddriver.
     */
    private List<String> additionalScanPackages = List.of();
  }
}
