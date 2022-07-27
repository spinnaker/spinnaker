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
import com.netflix.spinnaker.kork.secrets.user.DefaultUserSecretSerde;
import com.netflix.spinnaker.kork.secrets.user.UserSecretData;
import com.netflix.spinnaker.kork.secrets.user.UserSecretSerde;
import com.netflix.spinnaker.kork.secrets.user.UserSecretSerdeFactory;
import com.netflix.spinnaker.kork.secrets.user.UserSecretType;
import com.netflix.spinnaker.kork.secrets.user.UserSecretTypeProvider;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ResourceLoader;

@Configuration
@ComponentScan
public class SecretConfiguration {

  @Bean
  static SecretBeanPostProcessor secretBeanPostProcessor(
      ConfigurableApplicationContext applicationContext, SecretManager secretManager) {
    return new SecretBeanPostProcessor(applicationContext, secretManager);
  }

  @Bean
  public UserSecretTypeProvider defaultUserSecretTypeProvider(ResourceLoader loader) {
    return UserSecretTypeProvider.fromPackage(UserSecretData.class.getPackageName(), loader);
  }

  @Bean
  public UserSecretSerde userSecretSerde(
      final List<UserSecretTypeProvider> userSecretTypeProviders) {
    List<ObjectMapper> mappers = List.of(new ObjectMapper(), new YAMLMapper(), new CBORMapper());
    Set<Class<? extends UserSecretData>> classes =
        userSecretTypeProviders.stream()
            .flatMap(UserSecretTypeProvider::getUserSecretTypes)
            .filter(type -> type != null && type.isAnnotationPresent(UserSecretType.class))
            .collect(Collectors.toSet());
    return new DefaultUserSecretSerde(mappers, classes);
  }

  @Bean
  public UserSecretSerdeFactory userSecretSerdeFactory(ObjectProvider<UserSecretSerde> serdes) {
    return new UserSecretSerdeFactory(serdes);
  }
}
