/*
 * Copyright 2026 Harness, Inc.
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

package com.netflix.spinnaker.clouddriver.lambda.cache.model;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.awsobjectmapper.AmazonObjectMapperConfigurer;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Verifies that {@link LambdaFunction}'s typed accessors expose the fields that the v1
 * implementation exposed (revisions, code, tags, etc.), and that they round-trip through the same
 * {@code convertValue} path used by {@code LambdaCacheClient}.
 */
class LambdaFunctionTest {

  private final ObjectMapper mapper = AmazonObjectMapperConfigurer.createConfigured();

  @Test
  void typedAccessorsRoundTripFromAttributes() {
    Map<String, Object> attributes =
        Map.of(
            "functionName", "testFunction",
            "functionArn", "arn:aws:lambda:us-west-2:123456789012:function:testFunction",
            "revisions", Map.of("rev-1", "1", "rev-2", "2"),
            "code", Map.of("repositoryType", "S3", "location", "https://example"),
            "tags", Map.of("app", "myapp", "stack", "prod"),
            "eventSourceMappings", List.of(Map.of("uuid", "u1")),
            "aliasConfigurations", List.of(Map.of("name", "live")),
            "targetGroups", List.of("tg-1"));

    LambdaFunction function = mapper.convertValue(attributes, LambdaFunction.class);

    assertThat(function.getFunctionName()).isEqualTo("testFunction");
    assertThat(function.getFunctionArn())
        .isEqualTo("arn:aws:lambda:us-west-2:123456789012:function:testFunction");
    assertThat(function.getRevisions()).containsEntry("rev-1", "1").containsEntry("rev-2", "2");
    assertThat(function.getCode())
        .containsEntry("repositoryType", "S3")
        .containsEntry("location", "https://example");
    assertThat(function.getTags()).containsEntry("app", "myapp").containsEntry("stack", "prod");
    assertThat(function.getEventSourceMappings()).hasSize(1);
    assertThat(function.getAliasConfigurations()).hasSize(1);
    assertThat(function.getTargetGroups()).containsExactly("tg-1");
  }

  @Test
  void settersPopulateAttributesForSerialization() {
    LambdaFunction function = new LambdaFunction();
    function.setRevisions(Map.of("rev-1", "1"));
    function.setCode(Map.of("repositoryType", "S3"));
    function.setTags(Map.of("app", "myapp"));

    // @JsonAnyGetter flattens attributes to top-level fields, preserving the v1 JSON shape.
    @SuppressWarnings("unchecked")
    Map<String, Object> serialized = mapper.convertValue(function, Map.class);

    assertThat(serialized).containsKey("revisions");
    assertThat(serialized).containsKey("code");
    assertThat(serialized).containsKey("tags");
    assertThat((Map<String, Object>) serialized.get("tags")).containsEntry("app", "myapp");
  }
}
