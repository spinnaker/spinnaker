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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;

@ExtendWith(MockitoExtension.class)
public class SecretManagerTest {

  @Mock ObjectProvider<SecretEngine> secretEngineProvider;

  SecretEngineRegistry secretEngineRegistry;

  @Mock SecretEngine secretEngine;

  SecretManager secretManager;

  @BeforeEach
  public void setup() {
    secretEngineRegistry = new SecretEngineRegistry(secretEngineProvider);
    lenient().when(secretEngineProvider.orderedStream()).thenReturn(Stream.of(secretEngine));
    lenient().when(secretEngine.identifier()).thenReturn("s3");
    secretManager = spy(new SecretManager(secretEngineRegistry));
  }

  @Test
  public void decryptTest() throws SecretDecryptionException {
    var secretConfig = "encrypted:s3!paramName:paramValue";
    when(secretEngine.decrypt(any(EncryptedSecret.class))).thenReturn("test".getBytes());
    assertEquals("test", secretManager.decrypt(secretConfig));
  }

  @Test
  public void decryptSecretEngineNotFound() throws SecretDecryptionException {
    String secretConfig = "encrypted:does-not-exist!paramName:paramValue";
    Exception exception =
        assertThrows(SecretDecryptionException.class, () -> secretManager.decrypt(secretConfig));
    assertTrue(exception.getMessage().contains("Secret Engine does not exist: does-not-exist"));
  }

  @Test
  public void decryptInvalidParams() throws SecretDecryptionException {
    doThrow(InvalidSecretFormatException.class)
        .when(secretEngine)
        .validate(any(EncryptedSecret.class));
    String secretConfig = "encrypted:s3!paramName:paramValue";
    assertThrows(InvalidSecretFormatException.class, () -> secretManager.decrypt(secretConfig));
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
    String secretConfig = "encrypted:does-not-exist!paramName:paramValue";
    Exception exception =
        assertThrows(
            SecretDecryptionException.class, () -> secretManager.decryptAsFile(secretConfig));
    assertTrue(exception.getMessage().contains("Secret Engine does not exist: does-not-exist"));
  }

  @Test
  public void decryptFileInvalidParams() throws SecretDecryptionException {
    doThrow(InvalidSecretFormatException.class)
        .when(secretEngine)
        .validate(any(EncryptedSecret.class));
    String secretConfig = "encrypted:s3!paramName:paramValue";
    assertThrows(
        InvalidSecretFormatException.class, () -> secretManager.decryptAsFile(secretConfig));
  }

  @Test
  public void decryptFileNoDiskSpaceMock() throws SecretDecryptionException {
    doThrow(SecretDecryptionException.class).when(secretManager).createTempFile(any(), any());
    doReturn("contents".getBytes(StandardCharsets.UTF_8)).when(secretManager).decryptAsBytes(any());
    doCallRealMethod().when(secretManager).decryptAsFile(any());
    String secretConfig = "encrypted:s3!paramName:paramValue";
    assertThrows(SecretDecryptionException.class, () -> secretManager.decryptAsFile(secretConfig));
  }
}
