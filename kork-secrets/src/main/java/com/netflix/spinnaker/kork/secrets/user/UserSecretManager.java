package com.netflix.spinnaker.kork.secrets.user;

import com.netflix.spinnaker.kork.secrets.SecretDecryptionException;
import com.netflix.spinnaker.kork.secrets.SecretEngine;
import com.netflix.spinnaker.kork.secrets.SecretEngineRegistry;
import javax.annotation.Nonnull;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Central component for obtaining user secrets by reference.
 *
 * @see UserSecretReference
 * @see UserSecret
 */
@Component
@RequiredArgsConstructor
public class UserSecretManager {
  private final SecretEngineRegistry registry;

  @Nonnull
  public UserSecret getUserSecret(@Nonnull UserSecretReference reference) {
    String engineIdentifier = reference.getEngineIdentifier();
    SecretEngine engine = registry.getEngine(engineIdentifier);
    if (engine == null) {
      throw new SecretDecryptionException("Unknown secret engine identifier: " + engineIdentifier);
    }
    engine.validate(reference);
    return engine.decrypt(reference);
  }
}
