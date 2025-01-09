/*
 * Copyright 2025 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
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

package com.netflix.spinnaker.kork.observability.registry;

import com.netflix.spinnaker.kork.observability.model.MeterRegistryConfig;
import com.netflix.spinnaker.kork.observability.service.TagsService;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;

@Slf4j
@Configuration
public class AddDefaultTagsRegistryCustomizer implements RegistryCustomizer {

  private final TagsService tagsService;

  public AddDefaultTagsRegistryCustomizer(TagsService tagsService) {
    this.tagsService = tagsService;
  }

  @Override
  public void customize(MeterRegistry registry, MeterRegistryConfig meterRegistryConfig) {
    if (meterRegistryConfig.isDefaultTagsDisabled()) {
      return;
    }

    log.info("Adding default tags to registry: {}", registry.getClass().getSimpleName());
    registry.config().commonTags(tagsService.getDefaultTags());
  }
}
