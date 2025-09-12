package com.netflix.spinnaker.kork.secrets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.netflix.spinnaker.kork.secrets.engines.NoopSecretEngine;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class SecretSessionTest {

  private SecretEngine secretEngine;
  private List<SecretEngine> secretEngineList = new ArrayList<>();
  private SecretManager secretManager;
  private SecretSession secretSession;

  @BeforeEach
  public void setup() {
    SecretEngineRegistry secretEngineRegistry = new SecretEngineRegistry(secretEngineList);

    secretManager = spy(new SecretManager(secretEngineRegistry));
    lenient().doCallRealMethod().when(secretManager).decrypt(any());

    secretEngine = spy(new NoopSecretEngine());
    lenient().doCallRealMethod().when(secretEngine).decrypt(any(EncryptedSecret.class));

    secretEngineList.add(secretEngine);
    secretSession = new SecretSession(secretManager);
    addTestValuesToSecretSessionCaches();
  }

  private void addTestValuesToSecretSessionCaches() {
    secretSession.addCachedSecret("encrypted:noop!f:test!k:key", "decrypted");
    secretSession.addCachedSecretFile("encrypted:noop!f:file", Paths.get("decryptedFile"));
  }

  @Test
  public void decryptReturnsSecretFromCache() {
    String decrypted = secretSession.decrypt("encrypted:noop!f:test!k:key");
    assertEquals("decrypted", decrypted);
  }

  @Test
  public void decryptAddsToCacheOnCacheMiss() {
    assertEquals(1, secretSession.secretCache.size());
    String decrypted = secretSession.decrypt("encrypted:noop!f:unknown!v:test");
    assertEquals("test", decrypted);
    assertEquals("test", secretSession.secretCache.get("encrypted:noop!f:unknown!v:test"));
    assertEquals(2, secretSession.secretCache.size());
  }

  @Test
  public void decryptAsFileReturnsSecretFromCache() {
    String decryptedPath = secretSession.decryptAsFile("encrypted:noop!f:file").toString();
    assertEquals("decryptedFile", decryptedPath);
  }

  @Test
  public void decryptAsFileAddsToCacheOnCacheMiss() {
    doReturn(Paths.get("tempFile")).when(secretManager).decryptAsFile(any());
    assertEquals(1, secretSession.secretFileCache.size());
    Path decrypted = secretSession.decryptAsFile("encrypted:noop!f:unknown");
    assertEquals(2, secretSession.secretFileCache.size());
  }

  @Test
  public void clearCachedSecretsShouldClearAllCaches() {
    assertEquals(1, secretSession.secretCache.size());
    assertEquals(1, secretSession.secretFileCache.size());
    secretSession.clearCachedSecrets();
    assertEquals(0, secretSession.secretCache.size());
    assertEquals(0, secretSession.secretFileCache.size());
    verify(secretEngine, times(1)).clearCache();
  }
}
