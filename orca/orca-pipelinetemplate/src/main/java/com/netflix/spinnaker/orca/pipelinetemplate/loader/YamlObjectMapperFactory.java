/*
 * Copyright 2017 Netflix, Inc.
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

package com.netflix.spinnaker.orca.pipelinetemplate.loader;

import com.netflix.spinnaker.kork.yaml.YamlHelper;
import org.snakeyaml.engine.v2.api.LoadSettings;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.MapperFeature;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.SerializationFeature;
import tools.jackson.databind.cfg.MapperBuilder;
import tools.jackson.dataformat.yaml.YAMLFactory;
import tools.jackson.dataformat.yaml.YAMLMapper;

final class YamlObjectMapperFactory {
  private YamlObjectMapperFactory() {}

  static ObjectMapper create(ObjectMapper objectMapper, YamlHelper yamlHelper) {
    var loaderOptions = yamlHelper.loaderOptions();
    LoadSettings loadSettings =
        LoadSettings.builder()
            .setMaxAliasesForCollections(loaderOptions.getMaxAliasesForCollections())
            .setCodePointLimit(loaderOptions.getCodePointLimit())
            .build();

    return copyConfig(
        YAMLMapper.builder(YAMLFactory.builder().loadSettings(loadSettings).build()), objectMapper);
  }

  static ObjectMapper create(ObjectMapper objectMapper) {
    return copyConfig(YAMLMapper.builder(), objectMapper);
  }

  /**
   * The pre-migration code derived the YAML mapper via {@code setConfig(...)} from the source
   * mapper, inheriting its feature flags. Restore that: copy all feature flags so callers that rely
   * on strict settings (e.g. FAIL_ON_UNKNOWN_PROPERTIES) keep them.
   */
  private static ObjectMapper copyConfig(MapperBuilder<?, ?> builder, ObjectMapper objectMapper) {
    builder.addModules(objectMapper.registeredModules());
    for (DeserializationFeature f : DeserializationFeature.values()) {
      builder.configure(f, objectMapper.isEnabled(f));
    }
    for (SerializationFeature f : SerializationFeature.values()) {
      builder.configure(f, objectMapper.isEnabled(f));
    }
    for (MapperFeature f : MapperFeature.values()) {
      builder.configure(f, objectMapper.isEnabled(f));
    }
    return builder.build();
  }
}
