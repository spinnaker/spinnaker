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
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.core.io.ResourceLoader;
import tools.jackson.databind.MapperFeature;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.cfg.DateTimeFeature;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.dataformat.cbor.CBORMapper;
import tools.jackson.dataformat.yaml.YAMLMapper;

@AutoConfiguration
@ComponentScan
public class SecretConfiguration {

  @Bean
  public UserSecretTypeProvider defaultUserSecretTypeProvider(ResourceLoader loader) {
    return UserSecretTypeProvider.fromPackage(UserSecretData.class.getPackageName(), loader);
  }

  @Bean
  public UserSecretSerde userSecretSerde(
      final List<UserSecretTypeProvider> userSecretTypeProviders) {
    // Secret bytes are persisted. Keep Jackson 2 property order and Date format.
    List<ObjectMapper> mappers =
        List.of(
            jsonMapper(),
            YAMLMapper.builder()
                .disable(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY)
                .disable(MapperFeature.SORT_CREATOR_PROPERTIES_FIRST)
                .enable(DateTimeFeature.WRITE_DATES_AS_TIMESTAMPS)
                .build(),
            CBORMapper.builder()
                .disable(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY)
                .disable(MapperFeature.SORT_CREATOR_PROPERTIES_FIRST)
                .enable(DateTimeFeature.WRITE_DATES_AS_TIMESTAMPS)
                .build());
    Set<Class<? extends UserSecretData>> classes =
        userSecretTypeProviders.stream()
            .flatMap(UserSecretTypeProvider::getUserSecretTypes)
            .filter(type -> type != null && type.isAnnotationPresent(UserSecretType.class))
            .collect(Collectors.toSet());
    return new DefaultUserSecretSerde(mappers, classes);
  }

  private static JsonMapper jsonMapper() {
    return JsonMapper.builder()
        .disable(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY)
        .disable(MapperFeature.SORT_CREATOR_PROPERTIES_FIRST)
        .enable(DateTimeFeature.WRITE_DATES_AS_TIMESTAMPS)
        .build();
  }

  @Bean
  public UserSecretSerdeFactory userSecretSerdeFactory(ObjectProvider<UserSecretSerde> serdes) {
    return new UserSecretSerdeFactory(serdes);
  }
}
