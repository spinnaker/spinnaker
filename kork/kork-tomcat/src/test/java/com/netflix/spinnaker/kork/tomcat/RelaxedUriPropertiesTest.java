/*
 * Copyright 2026 Netflix, Inc.
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
 *
 */

package com.netflix.spinnaker.kork.tomcat;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.netflix.spinnaker.kork.tomcat.x509.SslExtensionConfigurationProperties;
import org.apache.catalina.connector.Connector;
import org.apache.coyote.http11.AbstractHttp11Protocol;
import org.junit.jupiter.api.Test;

/**
 * Drives {@link DefaultTomcatConnectorCustomizer} against a real Tomcat HTTP/1.1 protocol handler
 * to verify that {@code relaxedQueryCharacters} is applied to Tomcat's relaxed *query* characters
 * (and not, as a prior copy/paste bug did, to the relaxed *path* characters).
 */
class RelaxedUriPropertiesTest {

  private AbstractHttp11Protocol<?> customizeWith(TomcatConfigurationProperties props) {
    DefaultTomcatConnectorCustomizer customizer =
        new DefaultTomcatConnectorCustomizer(props, new SslExtensionConfigurationProperties());
    Connector connector = new Connector();
    customizer.applyRelaxedURIProperties(connector);
    return (AbstractHttp11Protocol<?>) connector.getProtocolHandler();
  }

  @Test
  void relaxedQueryCharactersAreAppliedToQueryChars() {
    TomcatConfigurationProperties props = new TomcatConfigurationProperties();
    props.setRelaxedQueryCharacters("[]");

    AbstractHttp11Protocol<?> protocol = customizeWith(props);

    String queryChars = protocol.getRelaxedQueryChars();
    assertTrue(
        queryChars.indexOf('[') >= 0, "expected '[' in relaxed query chars but got: " + queryChars);
    assertTrue(
        queryChars.indexOf(']') >= 0, "expected ']' in relaxed query chars but got: " + queryChars);

    // Regression guard: the query characters must NOT leak into the path characters,
    // which is what the previous setRelaxedPathChars(getRelaxedPathCharacters()) bug caused.
    // With only relaxedQueryCharacters configured, the path chars are left untouched (null).
    String pathChars = protocol.getRelaxedPathChars();
    assertFalse(
        pathChars != null && pathChars.indexOf('[') >= 0,
        "query chars must not be applied to path chars: " + pathChars);
  }

  @Test
  void relaxedPathCharactersAreAppliedToPathChars() {
    TomcatConfigurationProperties props = new TomcatConfigurationProperties();
    props.setRelaxedPathCharacters("{}");

    AbstractHttp11Protocol<?> protocol = customizeWith(props);

    String pathChars = protocol.getRelaxedPathChars();
    assertTrue(
        pathChars.indexOf('{') >= 0, "expected '{' in relaxed path chars but got: " + pathChars);
    assertTrue(
        pathChars.indexOf('}') >= 0, "expected '}' in relaxed path chars but got: " + pathChars);
  }
}
