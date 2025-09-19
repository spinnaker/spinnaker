/*
 * Copyright 2022 Apple Inc.
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

package com.netflix.spinnaker.kork.secrets.user;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.netflix.spinnaker.kork.secrets.EncryptedSecret;
import com.netflix.spinnaker.kork.secrets.SecretConfiguration;
import com.netflix.spinnaker.kork.secrets.engines.NoopSecretParameter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit.jupiter.SpringExtension;

@SpringBootTest(classes = SecretConfiguration.class)
@ExtendWith(SpringExtension.class)
public class UserSecretManagerTest {

  @Autowired UserSecretManager userSecretManager;

  @Test
  public void getTestSecret() {
    var ref = UserSecretReference.parse("secret://noop?v=test");
    var secret = userSecretManager.getUserSecret(ref);
    assertEquals("test", ref.getParameter(NoopSecretParameter.VALUE));
    assertEquals("test", secret.getSecretString(ref));
    assertEquals("opaque", secret.getType());
    assertTrue(secret.getRoles().isEmpty());
  }

  @Test
  public void getTestSecretString() {
    var ref = UserSecretReference.parse("secret://noop?v=bar");
    var userSecret = userSecretManager.getUserSecret(ref);
    assertEquals("bar", ref.getParameter(NoopSecretParameter.VALUE));
    assertEquals("bar", userSecret.getSecretString(ref));
  }

  @Test
  public void getTestExternalSecret() {
    var ref = EncryptedSecret.parse("encrypted:noop!v:test");
    assertNotNull(ref);
    var secret = userSecretManager.getExternalSecretString(ref);
    assertEquals("test", secret);
  }
}
