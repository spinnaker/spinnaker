/*
 * Copyright 2026 Harness, Inc.
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

package com.netflix.spinnaker.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import tools.jackson.databind.json.JsonMapper;

class Jackson2CompatibilityConfigurationTest {

  @com.fasterxml.jackson.databind.annotation.JsonDeserialize(builder = Widget.WidgetBuilder.class)
  public static class Widget {
    public final String name;
    public final Map<String, Object> metadata;

    private Widget(String name, Map<String, Object> metadata) {
      this.name = name;
      this.metadata = metadata;
    }

    @com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder(withPrefix = "")
    @com.fasterxml.jackson.annotation.JsonIgnoreProperties("kind")
    public static class WidgetBuilder {
      private String name;
      private Map<String, Object> metadata = new HashMap<>();

      public WidgetBuilder name(String name) {
        this.name = name;
        return this;
      }

      @com.fasterxml.jackson.annotation.JsonAnySetter
      public WidgetBuilder putMetadata(String key, Object value) {
        metadata.put(key, value);
        return this;
      }

      public Widget build() {
        return new Widget(name, metadata);
      }
    }
  }

  private final ApplicationContextRunner runner =
      new ApplicationContextRunner()
          .withConfiguration(
              org.springframework.boot.context.annotation.UserConfigurations.of(
                  Jackson2CompatibilityConfiguration.class));

  @Test
  void customizerIsRegistered() {
    runner.run(
        ctx ->
            assertThat(ctx)
                .hasSingleBean(
                    org.springframework.boot.jackson.autoconfigure.JsonMapperBuilderCustomizer
                        .class));
  }

  @Test
  void jackson2BuilderAnnotationsWorkOnJackson3Mapper() throws Exception {
    JsonMapper.Builder builder = JsonMapper.builder();
    new Jackson2CompatibilityConfiguration()
        .jackson2AnnotationCompatibilityCustomizer()
        .customize(builder);
    JsonMapper mapper = builder.build();

    Widget widget =
        mapper.readValue("{\"name\":\"w\",\"kind\":\"ignored\",\"extra\":\"kept\"}", Widget.class);

    assertThat(widget.name).isEqualTo("w");
    assertThat(widget.metadata).containsEntry("extra", "kept");
  }
}
