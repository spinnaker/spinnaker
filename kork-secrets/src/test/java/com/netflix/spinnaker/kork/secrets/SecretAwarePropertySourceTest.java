package com.netflix.spinnaker.kork.secrets;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.springframework.core.env.EnumerablePropertySource;

public class SecretAwarePropertySourceTest {

  private SecretAwarePropertySource secretAwarePropertySource;
  private SecretManager secretManager;
  private Map<String, String> testValues = new HashMap<>();

  @Rule public ExpectedException thrown = ExpectedException.none();

  @Before
  public void setup() {
    EnumerablePropertySource source =
        new EnumerablePropertySource("testSource") {
          @Override
          public String[] getPropertyNames() {
            return new String[0];
          }

          @Override
          public Object getProperty(String name) {
            return testValues.get(name);
          }
        };

    testValues.put("testSecretFile", "encrypted:noop!k:testValue");
    testValues.put("testSecretPath", "encrypted:noop!k:testValue");
    testValues.put("testSecretCert", "encryptedFile:noop!k:testValue");
    testValues.put("testSecretString", "encrypted:noop!k:testValue");
    testValues.put("testNotSoSecret", "unencrypted");

    secretManager = mock(SecretManager.class);
    secretAwarePropertySource = new SecretAwarePropertySource(source, secretManager);

    when(secretManager.decryptAsFile(any())).thenReturn(Paths.get("decryptedFile"));
    when(secretManager.decrypt(any())).thenReturn("decryptedString");
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
    secretAwarePropertySource.setSecretManager(null);
    thrown.expect(SecretException.class);
    thrown.expectMessage("No secret manager to decrypt value of testSecretString");
    secretAwarePropertySource.getProperty("testSecretString");
  }
}
