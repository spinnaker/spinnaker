/*
 * Copyright 2025 OpsMx, Inc.
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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.EnumerablePropertySource;
import org.springframework.core.env.PropertySource;
import org.springframework.core.type.AnnotatedTypeMetadata;
import org.springframework.mock.env.MockPropertySource;

class OAuthConfigEnabledTest {

  private OAuthConfigEnabled condition;

  @Mock private ConfigurableEnvironment environment;
  @Mock private AnnotatedTypeMetadata metadata;
  @Mock private ConditionContext context;

  @BeforeEach
  void setUp() {
    MockitoAnnotations.openMocks(this);
    condition = new OAuthConfigEnabled();
    when(context.getEnvironment()).thenReturn(environment);
  }

  @Test
  void testMatches_WhenOAuth2ConfigExists_ShouldReturnTrue() {
    MockPropertySource propertySource = new MockPropertySource();
    propertySource.setProperty(
        "spring.security.oauth2.client.registration.test-client.client-id", "test-client-id");

    when(environment.getPropertySources()).thenReturn(new MockPropertySources(propertySource));

    boolean result = condition.matches(context, metadata);
    assertThat(result).isTrue();
  }

  @Test
  void testMatches_WhenNoOAuth2Config_ShouldReturnFalse() {
    MockPropertySource propertySource = new MockPropertySource();
    propertySource.setProperty("some.other.property", "value");
    when(environment.getPropertySources()).thenReturn(new MockPropertySources(propertySource));

    boolean result = condition.matches(context, metadata);
    assertThat(result).isFalse();
  }

  @Test
  void testMatches_WhenEnvironmentNotConfigurable_ShouldReturnFalse() {
    var nonConfigurableEnv = mock(org.springframework.core.env.Environment.class);
    when(context.getEnvironment()).thenReturn(nonConfigurableEnv);

    boolean result = condition.matches(context, metadata);
    assertThat(result).isFalse();
  }

  @Test
  void testMatches_WithEnumerablePropertySource_ShouldReturnTrue() {

    @SuppressWarnings("unchecked")
    EnumerablePropertySource<Map<String, Object>> propertySource =
        mock(EnumerablePropertySource.class);
    when(propertySource.getPropertyNames())
        .thenReturn(
            new String[] {"spring.security.oauth2.client.registration.test-client.client-id"});

    when(environment.getPropertySources()).thenReturn(new MockPropertySources(propertySource));

    boolean result = condition.matches(context, metadata);
    assertThat(result).isTrue();
  }

  @Test
  void testMatches_WithEnumerablePropertySourceButNoMatchingProperties_ShouldReturnFalse() {

    @SuppressWarnings("unchecked")
    EnumerablePropertySource<Map<String, Object>> propertySource =
        mock(EnumerablePropertySource.class);
    when(propertySource.getPropertyNames()).thenReturn(new String[] {"some.unrelated.property"});

    when(environment.getPropertySources()).thenReturn(new MockPropertySources(propertySource));

    boolean result = condition.matches(context, metadata);
    assertThat(result).isFalse();
  }

  // Helper class to mock property sources
  private static class MockPropertySources
      extends org.springframework.core.env.MutablePropertySources {
    MockPropertySources(PropertySource<?>... propertySources) {
      for (PropertySource<?> ps : propertySources) {
        addLast(ps);
      }
    }
  }
}
