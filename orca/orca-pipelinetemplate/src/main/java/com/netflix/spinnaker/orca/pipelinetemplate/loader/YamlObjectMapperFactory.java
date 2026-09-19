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
import tools.jackson.databind.ObjectMapper;
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

    return YAMLMapper.builder(YAMLFactory.builder().loadSettings(loadSettings).build())
        .addModules(objectMapper.registeredModules())
        .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
        .build();
  }

  static ObjectMapper create(ObjectMapper objectMapper) {
    return YAMLMapper.builder()
        .addModules(objectMapper.registeredModules())
        .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
        .build();
  }
}
