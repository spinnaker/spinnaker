package com.netflix.spinnaker.kork.secrets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.env.MapPropertySource;

@ExtendWith(MockitoExtension.class)
public class SecretAwarePropertySourceTest {

  private SecretAwarePropertySource<Map<String, Object>> secretAwarePropertySource;
  private final SecretPropertyProcessor secretPropertyProcessor = new SecretPropertyProcessor();
  @Mock private SecretManager secretManager;
  private final Map<String, Object> testValues = new HashMap<>();
  private final MapPropertySource propertySource = new MapPropertySource("testSource", testValues);

  @BeforeEach
  public void setup() {
    testValues.put("testSecretFile", "encrypted:noop!k:testValue");
    testValues.put("testSecretPath", "encrypted:noop!k:testValue");
    testValues.put("testSecretCert", "encryptedFile:noop!k:testValue");
    testValues.put("testSecretString", "encrypted:noop!k:testValue");
    testValues.put("testNotSoSecret", "unencrypted");

    secretManager = mock(SecretManager.class);
    secretPropertyProcessor.setSecretManager(secretManager);
    secretAwarePropertySource =
        new SecretAwarePropertySource<>(propertySource, secretPropertyProcessor);

    lenient().when(secretManager.decryptAsFile(any())).thenReturn(Paths.get("decryptedFile"));
    lenient().when(secretManager.decrypt(any())).thenReturn("decryptedString");
  }

  @Test
  public void secretPropertyShouldGetDecrypted() {
    secretAwarePropertySource.getProperty("testSecretString");
    verify(secretManager, times(1)).decrypt(any());
    verify(secretManager, never()).decryptAsFile(any());
  }

  @Test
  public void secretFileShouldGetDecryptedAsFile() {
    secretAwarePropertySource.getProperty("testSecretFile");
    verify(secretManager, never()).decrypt(any());
    verify(secretManager, times(1)).decryptAsFile(any());
  }

  @Test
  public void secretPathShouldGetDecryptedAsFile() {
    secretAwarePropertySource.getProperty("testSecretPath");
    verify(secretManager, never()).decrypt(any());
    verify(secretManager, times(1)).decryptAsFile(any());
  }

  @Test
  public void secretFileInPropertyValueShouldGetDecryptedAsFile() {
    secretAwarePropertySource.getProperty("testSecretCert");
    verify(secretManager, never()).decrypt(any());
    verify(secretManager, times(1)).decryptAsFile(any());
  }

  @Test
  public void unencryptedPropertyShouldDoNothing() {
    String notSecretKey = "testNotSoSecret";
    Object returnedValue = secretAwarePropertySource.getProperty(notSecretKey);
    verify(secretManager, never()).decrypt(any());
    verify(secretManager, never()).decryptAsFile(any());
    assertEquals(testValues.get(notSecretKey), returnedValue);
  }

  @Test
  public void noSecretManagerShouldThrowException() {
    secretPropertyProcessor.setSecretManager(null);
    SecretException exception =
        assertThrows(
            SecretException.class, () -> secretAwarePropertySource.getProperty("testSecretString"));
    assertEquals("No secret manager to decrypt value of testSecretString", exception.getMessage());
    verify(secretManager, never()).decrypt(any());
    verify(secretManager, never()).decryptAsFile(any());
  }
}
