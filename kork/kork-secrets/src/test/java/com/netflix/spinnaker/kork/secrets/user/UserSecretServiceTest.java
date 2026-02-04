/*
 * Copyright 2023 Apple, Inc.
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

package com.netflix.spinnaker.kork.secrets.user;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.BDDMockito.given;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.netflix.spinnaker.kork.secrets.SecretEngine;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

@SpringJUnitConfig
class UserSecretServiceTest {
  @MockBean SecretEngine mockSecretEngine;
  @Autowired UserSecretService userSecretService;

  UserSecret secret =
      UserSecret.builder()
          .data(new StringUserSecretData("super-secret"))
          .metadata(UserSecretMetadata.builder().type("string").build())
          .build();

  @BeforeEach
  void setUp() {
    given(mockSecretEngine.identifier()).willReturn("mock");
  }

  @Test
  void tracksUserSecret() {
    String uri = "secret://mock?n=secret-name";
    UserSecretReference ref = UserSecretReference.parse(uri);
    given(mockSecretEngine.decrypt(ref)).willReturn(secret);

    String resourceId = "some-resource-id";
    assertFalse(userSecretService.isTrackingUserSecretsForResource(resourceId));
    ObjectNode resource =
        JsonNodeFactory.instance.objectNode().put("id", resourceId).put("secret", uri);
    ObjectNode updated = userSecretService.replaceSecretReferences(resourceId, resource, Set.of());
    String secretString = updated.required("secret").textValue();
    assertEquals("super-secret", secretString);
    assertTrue(userSecretService.isTrackingUserSecretsForResource(resourceId));

    // do another round trip to ensure user secrets tracking updates
    userSecretService.replaceSecretReferences(resourceId, updated, Set.of());
    assertFalse(userSecretService.isTrackingUserSecretsForResource(resourceId));
  }

  @Test
  void ignoresRequestedFieldsWhenReplacingSecrets() {
    String uri = "secret://mock?n=secret-name";
    UserSecretReference ref = UserSecretReference.parse(uri);
    given(mockSecretEngine.decrypt(ref)).willReturn(secret);

    String resourceId = "some-resource-id";
    ObjectNode resource =
        JsonNodeFactory.instance
            .objectNode()
            .put("id", resourceId)
            .put("replaced-secret", uri)
            .put("kept-secret", uri);
    ObjectNode updated =
        userSecretService.replaceSecretReferences(resourceId, resource, Set.of("kept-secret"));
    // this secret field is replaced with the secret value
    String replacedSecretString = updated.required("replaced-secret").textValue();
    assertEquals("super-secret", replacedSecretString);

    // this one keeps the secret:// URI untouched
    String keptSecretString = updated.required("kept-secret").textValue();
    assertNotEquals("super-secret", keptSecretString);
    assertEquals(uri, keptSecretString);
  }

  @Configuration(proxyBeanMethods = false)
  @EnableAutoConfiguration
  static class TestConfig {}
}
