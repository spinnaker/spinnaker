/*
 * Copyright 2019 Armory, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.kork.secrets;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.cbor.databind.CBORMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import com.netflix.spinnaker.kork.secrets.user.UserSecret;
import com.netflix.spinnaker.kork.secrets.user.UserSecretMapper;
import com.netflix.spinnaker.kork.secrets.user.UserSecretMixin;
import com.netflix.spinnaker.kork.secrets.user.UserSecretTypeProvider;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ResourceLoader;
import org.springframework.core.type.filter.AssignableTypeFilter;
import org.springframework.util.ClassUtils;

@Configuration
@ComponentScan
@Log4j2
public class SecretConfiguration {

  @Bean
  static SecretBeanPostProcessor secretBeanPostProcessor(
      ConfigurableApplicationContext applicationContext, SecretManager secretManager) {
    return new SecretBeanPostProcessor(applicationContext, secretManager);
  }

  @Bean
  public UserSecretTypeProvider defaultUserSecretTypeProvider(ResourceLoader loader) {
    var provider = new ClassPathScanningCandidateComponentProvider(false);
    provider.setResourceLoader(loader);
    provider.addIncludeFilter(new AssignableTypeFilter(UserSecret.class));
    return () ->
        provider.findCandidateComponents(UserSecret.class.getPackageName()).stream()
            .map(BeanDefinition::getBeanClassName)
            .filter(Objects::nonNull)
            .map(className -> tryLoadUserSecretClass(className, loader.getClassLoader()))
            .filter(Objects::nonNull);
  }

  @Nullable
  private static Class<? extends UserSecret> tryLoadUserSecretClass(
      @Nonnull String className, @Nullable ClassLoader classLoader) {
    try {
      return ClassUtils.forName(className, classLoader).asSubclass(UserSecret.class);
    } catch (ClassNotFoundException e) {
      log.error(
          "Unable to load discovered UserSecret class {}. User secrets with this type will not be parseable.",
          className,
          e);
      return null;
    }
  }

  @Bean
  public UserSecretMapper userSecretMapper(
      final List<UserSecretTypeProvider> userSecretTypeProviders) {
    List<ObjectMapper> mappers = List.of(new ObjectMapper(), new YAMLMapper(), new CBORMapper());
    Set<Class<?>> classes =
        userSecretTypeProviders.stream()
            .flatMap(UserSecretTypeProvider::getUserSecretTypes)
            .filter(Objects::nonNull)
            .collect(Collectors.toSet());
    mappers.forEach(
        mapper ->
            mapper.addMixIn(UserSecret.class, UserSecretMixin.class).registerSubtypes(classes));
    return new UserSecretMapper(mappers);
  }
}
