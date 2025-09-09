package com.netflix.spinnaker.kork.secrets;

import com.netflix.spinnaker.kork.annotations.NonnullByDefault;
import com.netflix.spinnaker.kork.secrets.user.UserSecretData;
import lombok.RequiredArgsConstructor;

/**
 * Used by the {@see NoopSecretEngine} to allow for bypassing any exceptions that may be thrown
 * during validation
 */
@NonnullByDefault
@RequiredArgsConstructor
public class NoopUserSecretData implements UserSecretData {
  private final String defaultValue;

  @Override
  public String getSecretString(String key) {
    return key;
  }

  @Override
  public String getSecretString() {
    return defaultValue;
  }
}
