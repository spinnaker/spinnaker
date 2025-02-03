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

package com.netflix.spinnaker.kork.actuator.observability.registry;

import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;

import com.netflix.spinnaker.kork.actuator.observability.model.MeterRegistryConfig;
import com.netflix.spinnaker.kork.actuator.observability.service.TagsService;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;

public class AddDefaultTagsRegistryCustomizerTest {

  @Mock TagsService tagsService;

  @Mock MeterRegistry registry;

  @Mock MeterRegistry.Config config;

  AddDefaultTagsRegistryCustomizer sut;

  @Before
  public void before() {
    initMocks(this);
    sut = new AddDefaultTagsRegistryCustomizer(tagsService);
    when(registry.config()).thenReturn(config);
  }

  @Test
  public void test_that_customize_adds_the_tags_to_the_registry_common_tag_config() {
    var tags = List.of(Tag.of("FOO", "BAR"));
    when(tagsService.getDefaultTags()).thenReturn(tags);
    sut.customize(registry, MeterRegistryConfig.builder().build());
    verify(config, times(1)).commonTags(tags);
  }

  @Test
  public void
      test_that_customize_does_not_adds_the_tags_to_the_registry_common_tag_config_when_disabled() {
    var tags = List.of(Tag.of("FOO", "BAR"));
    when(tagsService.getDefaultTags()).thenReturn(tags);
    sut.customize(registry, MeterRegistryConfig.builder().defaultTagsDisabled(true).build());
    verify(config, times(0)).commonTags(tags);
  }
}
