/*
 * Copyright 2025 Harness, Inc.
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

package com.netflix.spinnaker.kork.actuator.observability.service;

import static org.junit.Assert.*;

import com.netflix.spinnaker.kork.actuator.observability.model.ObservabilityConfigurationProperties;
import com.netflix.spinnaker.kork.actuator.observability.version.VersionResolver;
import io.micrometer.core.instrument.Tag;
import java.util.List;
import java.util.Map;
import org.junit.Test;

public class TagsServiceTest {

  private VersionResolver noOpVersionResolver =
      new VersionResolver() {
        @Override
        public String resolve(String serviceName) {
          return null;
        }

        @Override
        public int getOrder() {
          return 0;
        }
      };

  @Test
  public void test_default_tags_include_lib_tag() {
    var props = new ObservabilityConfigurationProperties();
    var tagsService = new TagsService(props, noOpVersionResolver, "test-app");

    List<Tag> tags = tagsService.getDefaultTags();
    Map<String, String> tagMap = tagsToMap(tags);

    assertEquals("kork-observability", tagMap.get(TagsService.LIB));
  }

  @Test
  public void test_default_tags_include_application_name_from_spring() {
    var props = new ObservabilityConfigurationProperties();
    var tagsService = new TagsService(props, noOpVersionResolver, "my-service");

    List<Tag> tags = tagsService.getDefaultTags();
    Map<String, String> tagMap = tagsToMap(tags);

    assertEquals("my-service", tagMap.get(TagsService.SPIN_SVC));
  }

  @Test
  public void test_default_tags_fallback_to_unknown_when_no_app_name() {
    var props = new ObservabilityConfigurationProperties();
    var tagsService = new TagsService(props, noOpVersionResolver, null);

    List<Tag> tags = tagsService.getDefaultTags();
    Map<String, String> tagMap = tagsToMap(tags);

    assertEquals("UNKNOWN", tagMap.get(TagsService.SPIN_SVC));
  }

  @Test
  public void test_additional_tags_are_included() {
    var props = new ObservabilityConfigurationProperties();
    props.getMetrics().setAdditionalTags(Map.of("environment", "prod", "region", "us-west-2"));
    var tagsService = new TagsService(props, noOpVersionResolver, "test-app");

    List<Tag> tags = tagsService.getDefaultTags();
    Map<String, String> tagMap = tagsToMap(tags);

    assertEquals("prod", tagMap.get("environment"));
    assertEquals("us-west-2", tagMap.get("region"));
  }

  @Test
  public void test_version_resolved_from_resolver_when_build_props_missing() {
    VersionResolver mockResolver =
        new VersionResolver() {
          @Override
          public String resolve(String serviceName) {
            return "1.2.3";
          }

          @Override
          public int getOrder() {
            return 0;
          }
        };
    var props = new ObservabilityConfigurationProperties();
    var tagsService = new TagsService(props, mockResolver, "test-app");

    List<Tag> tags = tagsService.getDefaultTags();
    Map<String, String> tagMap = tagsToMap(tags);

    assertEquals("1.2.3", tagMap.get("version"));
  }

  @Test
  public void test_null_and_empty_tags_are_filtered_out() {
    var props = new ObservabilityConfigurationProperties();
    props.getMetrics().setAdditionalTags(Map.of("valid", "value"));
    var tagsService = new TagsService(props, noOpVersionResolver, "test-app");

    List<Tag> tags = tagsService.getDefaultTags();

    // Should not contain hostname if HOSTNAME env var is not set
    // All tags should have non-null, non-empty values
    for (Tag tag : tags) {
      assertNotNull("Tag key should not be null", tag.getKey());
      assertNotNull("Tag value should not be null", tag.getValue());
      assertFalse("Tag value should not be empty", tag.getValue().isEmpty());
    }
  }

  @Test
  public void test_default_tags_are_immutable() {
    var props = new ObservabilityConfigurationProperties();
    var tagsService = new TagsService(props, noOpVersionResolver, "test-app");

    List<Tag> tags = tagsService.getDefaultTags();

    try {
      tags.add(Tag.of("new", "tag"));
      fail("Expected UnsupportedOperationException when modifying immutable list");
    } catch (UnsupportedOperationException e) {
      // Expected
    }
  }

  private Map<String, String> tagsToMap(List<Tag> tags) {
    return tags.stream()
        .collect(java.util.stream.Collectors.toMap(Tag::getKey, Tag::getValue, (a, b) -> a));
  }
}
