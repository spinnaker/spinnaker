/*
 * Copyright 2026 Wise, PLC.
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

package com.netflix.spinnaker.gate.security.oauth2;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class SimpleOAuth2UserTest {

  @Test
  void shouldReturnEmptyAttributesWhenConstructedWithNull() {
    SimpleOAuth2User user = new SimpleOAuth2User(null);

    assertThat(user.getAttributes()).isEmpty();
  }

  @Test
  void shouldReturnProvidedAttributes() {
    Map<String, Object> attributes = Map.of("email", "test@example.com", "login", "testuser");

    SimpleOAuth2User user = new SimpleOAuth2User(attributes);

    assertThat(user.getAttributes()).isEqualTo(attributes);
  }

  @Test
  void shouldReturnEmptyAuthorities() {
    SimpleOAuth2User user = new SimpleOAuth2User(Map.of());

    assertThat(user.getAuthorities()).isEmpty();
  }

  @Test
  void shouldReturnNameFromNameAttribute() {
    Map<String, Object> attributes = new HashMap<>();
    attributes.put("name", "Test User");
    attributes.put("login", "testuser");
    attributes.put("email", "test@example.com");

    SimpleOAuth2User user = new SimpleOAuth2User(attributes);

    assertThat(user.getName()).isEqualTo("Test User");
  }

  @Test
  void shouldReturnLoginWhenNameNotPresent() {
    Map<String, Object> attributes = new HashMap<>();
    attributes.put("login", "testuser");
    attributes.put("email", "test@example.com");

    SimpleOAuth2User user = new SimpleOAuth2User(attributes);

    assertThat(user.getName()).isEqualTo("testuser");
  }

  @Test
  void shouldReturnEmailWhenNameAndLoginNotPresent() {
    Map<String, Object> attributes = Map.of("email", "test@example.com");

    SimpleOAuth2User user = new SimpleOAuth2User(attributes);

    assertThat(user.getName()).isEqualTo("test@example.com");
  }

  @Test
  void shouldReturnNullWhenNoNameAttributes() {
    Map<String, Object> attributes = Map.of("other", "value");

    SimpleOAuth2User user = new SimpleOAuth2User(attributes);

    assertThat(user.getName()).isNull();
  }

  @Test
  void shouldHandleNullNameAttribute() {
    Map<String, Object> attributes = new HashMap<>();
    attributes.put("name", null);
    attributes.put("login", "testuser");

    SimpleOAuth2User user = new SimpleOAuth2User(attributes);

    assertThat(user.getName()).isEqualTo("testuser");
  }
}
