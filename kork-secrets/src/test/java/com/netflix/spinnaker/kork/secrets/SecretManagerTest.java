/*
 * Copyright 2019 Armory, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
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

package com.netflix.spinnaker.kork.secrets;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Path;
import org.junit.*;
import org.junit.Before;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

public class SecretManagerTest {

  @Mock SecretEngineRegistry secretEngineRegistry;

  @Mock SecretEngine secretEngine;

  SecretManager secretManager;

  @Rule public ExpectedException exceptionRule = ExpectedException.none();

  @Before
  public void setup() {
    MockitoAnnotations.initMocks(this);
    when(secretEngineRegistry.getEngine("s3")).thenReturn(secretEngine);
    when(secretEngine.identifier()).thenReturn("s3");
    secretManager = new SecretManager(secretEngineRegistry);
    // secretManager.setSecretEngineRegistry(secretEngineRegistry);
  }

  @Test
  public void decryptTest() throws SecretDecryptionException {
    var secretConfig = "encrypted:s3!paramName:paramValue";
    when(secretEngine.decrypt(any(EncryptedSecret.class))).thenReturn("test".getBytes());
    assertEquals("test", secretManager.decrypt(secretConfig));
  }

  @Test
  public void decryptSecretEngineNotFound() throws SecretDecryptionException {
    when(secretEngineRegistry.getEngine("does-not-exist")).thenReturn(null);
    String secretConfig = "encrypted:does-not-exist!paramName:paramValue";
    exceptionRule.expect(SecretDecryptionException.class);
    exceptionRule.expectMessage("Secret Engine does not exist: does-not-exist");
    secretManager.decrypt(secretConfig);
  }

  @Test
  public void decryptInvalidParams() throws SecretDecryptionException {
    doThrow(InvalidSecretFormatException.class)
        .when(secretEngine)
        .validate(any(EncryptedSecret.class));
    String secretConfig = "encrypted:s3!paramName:paramValue";
    exceptionRule.expect(InvalidSecretFormatException.class);
    secretManager.decrypt(secretConfig);
  }

  @Test
  public void decryptFile() throws SecretDecryptionException, IOException {
    String secretConfig = "encrypted:s3!paramName:paramValue";
    when(secretEngine.decrypt(any(EncryptedSecret.class))).thenReturn("test".getBytes());
    Path path = secretManager.decryptAsFile(secretConfig);
    assertTrue(path.toAbsolutePath().toString().matches(".*.secret$"));
    BufferedReader reader = new BufferedReader(new FileReader(path.toFile()));
    assertEquals("test", reader.readLine());
    reader.close();
  }

  @Test
  public void decryptFileSecretEngineNotFound() throws SecretDecryptionException {
    when(secretEngineRegistry.getEngine("does-not-exist")).thenReturn(null);
    String secretConfig = "encrypted:does-not-exist!paramName:paramValue";
    exceptionRule.expect(SecretDecryptionException.class);
    exceptionRule.expectMessage("Secret Engine does not exist: does-not-exist");
    secretManager.decryptAsFile(secretConfig);
  }

  @Test
  public void decryptFileInvalidParams() throws SecretDecryptionException {
    doThrow(InvalidSecretFormatException.class)
        .when(secretEngine)
        .validate(any(EncryptedSecret.class));
    String secretConfig = "encrypted:s3!paramName:paramValue";
    exceptionRule.expect(InvalidSecretFormatException.class);
    secretManager.decryptAsFile(secretConfig);
  }

  @Test
  public void decryptFileNoDiskSpaceMock() throws SecretDecryptionException {
    SecretManager spy = spy(new SecretManager(secretEngineRegistry));
    doThrow(SecretDecryptionException.class).when(spy).createTempFile(any(), any());
    doReturn("contents").when(spy).decrypt(any());
    doCallRealMethod().when(spy).decryptAsFile(any());
    exceptionRule.expect(SecretDecryptionException.class);
    String secretConfig = "encrypted:s3!paramName:paramValue";
    spy.decryptAsFile(secretConfig);
  }
}
