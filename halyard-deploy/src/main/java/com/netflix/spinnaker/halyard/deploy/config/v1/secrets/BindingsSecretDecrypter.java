package com.netflix.spinnaker.halyard.deploy.config.v1.secrets;

import com.netflix.spinnaker.halyard.core.secrets.v1.SecretSessionManager;
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.profile.Profile;
import com.netflix.spinnaker.kork.secrets.EncryptedSecret;
import java.nio.file.Path;
import org.apache.commons.lang3.RandomStringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class BindingsSecretDecrypter {
  private SecretSessionManager secretSessionManager;

  @Autowired
  BindingsSecretDecrypter(SecretSessionManager secretSessionManager) {
    this.secretSessionManager = secretSessionManager;
  }

  public String trackSecretFile(Profile profile, Path outputDir, String value, String fieldName) {
    if (!EncryptedSecret.isEncryptedSecret(value)) {
      return value;
    }
    String decryptedFilename = newRandomFileName(fieldName);
    profile.getDecryptedFiles().put(decryptedFilename, secretSessionManager.decryptAsBytes(value));
    return outputDir.resolve(decryptedFilename).toString();
  }

  private String newRandomFileName(String fieldName) {
    return fieldName + "-" + RandomStringUtils.randomAlphanumeric(5);
  }
}
