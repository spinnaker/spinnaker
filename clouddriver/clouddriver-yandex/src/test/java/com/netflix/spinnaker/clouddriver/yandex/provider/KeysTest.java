/*
 * Copyright 2020 YANDEX LLC
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

package com.netflix.spinnaker.clouddriver.yandex.provider;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Map;
import org.junit.jupiter.api.Test;

class KeysTest {
  @Test
  void testParseKey() {
    String key = Keys.getClusterKey("account", "app", "app-stack-detail");
    assertEquals("yandex:clusters:account:app:app-stack-detail", key);
    Map<String, String> result = Keys.parse(key);

    assertNotNull(result);
    assertEquals("app", result.get("application"));
    assertEquals("yandex", result.get("provider"));
    assertEquals("clusters", result.get("type"));
    assertEquals("stack", result.get("stack"));
    assertEquals("detail", result.get("detail"));
  }
}
