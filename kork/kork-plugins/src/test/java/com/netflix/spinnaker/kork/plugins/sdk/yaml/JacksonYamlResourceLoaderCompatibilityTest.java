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

package com.netflix.spinnaker.kork.plugins.sdk.yaml;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.netflix.spinnaker.kork.yaml.YamlHelper;
import com.netflix.spinnaker.kork.yaml.YamlParserProperties;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import tools.jackson.core.JacksonException;

class JacksonYamlResourceLoaderCompatibilityTest {

  @AfterEach
  void resetLimits() {
    new YamlHelper(new YamlParserProperties());
  }

  @Test
  void resourceLoadingPreservesLegacyScalarsAndExplicitStrings() {
    var loader = new JacksonYamlResourceLoader(getClass());
    Map<?, ?> values = loader.loadResource("yaml/legacy-scalars.yml", Map.class);

    assertEquals(420, values.get("mode"));
    assertEquals(10, values.get("capacity"));
    assertEquals(true, values.get("enabled"));
    assertEquals(false, values.get("disabled"));
    assertEquals("0644", values.get("quoted"));
    assertEquals("yes", values.get("tagged"));
    assertEquals(12, values.get("explicit"));
  }

  @Test
  void malformedLegacyNumbersStillFailForStringProperties() {
    var loader = new JacksonYamlResourceLoader(getClass());
    assertThrows(
        JacksonException.class,
        () -> loader.loadResource("yaml/invalid-scalar.yml", StringValue.class));
  }

  @Test
  void resourceLoadingHonorsConfiguredCodePointLimit() {
    var properties = new YamlParserProperties();
    properties.setCodePointLimit(16);
    new YamlHelper(properties);
    var loader = new JacksonYamlResourceLoader(getClass());

    assertEquals("hello", loader.loadResource("yaml/test.yml", Map.class).get("name"));
    assertThrows(
        JacksonException.class,
        () -> loader.loadResource("yaml/legacy-scalars.yml", Map.class));
  }

  public static class StringValue {
    public String value;
  }
}
