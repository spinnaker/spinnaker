package com.netflix.spinnaker.kork.secrets;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

public class SecretSession {
  private SecretManager secretManager;
  Map<String, String> secretCache = new HashMap<>();
  Map<String, Path> secretFileCache = new HashMap<>();

  public SecretSession(SecretManager secretManager) {
    this.secretManager = secretManager;
  }

  public String decrypt(String encryptedSecret) {
    if (secretCache.containsKey(encryptedSecret)) {
      return secretCache.get(encryptedSecret);
    } else {
      String decryptedValue = secretManager.decrypt(encryptedSecret);
      addCachedSecret(encryptedSecret, decryptedValue);
      return decryptedValue;
    }
  }

  public Path decryptAsFile(String encryptedSecret) {
    if (secretFileCache.containsKey(encryptedSecret)) {
      return secretFileCache.get(encryptedSecret);
    }

    Path decryptedFile = secretManager.decryptAsFile(encryptedSecret);
    addCachedSecretFile(encryptedSecret, decryptedFile);

    return decryptedFile;
  }

  public byte[] decryptAsBytes(String encrypted) {
    return secretManager.decryptAsBytes(encrypted);
  }

  public void clearCachedSecrets() {
    secretCache.clear();
    secretFileCache.clear();
    for (SecretEngine se : secretManager.getSecretEngineRegistry().getSecretEngineList()) {
      se.clearCache();
    }
  }

  public void addCachedSecret(String encrypted, String decrypted) {
    secretCache.put(encrypted, decrypted);
  }

  public void addCachedSecretFile(String encrypted, Path decrypted) {
    secretFileCache.put(encrypted, decrypted);
  }
}
