package com.netflix.spinnaker.kork.secrets.user;

import com.netflix.spinnaker.kork.secrets.SecretEngine;
import java.util.Collection;
import javax.annotation.Nonnull;

/**
 * User secrets are externally stored secrets with additional user-provided metadata regarding who
 * is authorized to use the contents of the secret along with defining the type of secret stored.
 * User secrets are stored in user-specific secrets managers supported by the {@link SecretEngine}
 * API. User secrets have a {@linkplain #getType() type} which corresponds to a specific
 * implementation type of this interface for use as a type discriminator. User secrets define a
 * collection of {@linkplain #getRoles() roles} allowed to use the contents of this secret.
 *
 * @see OpaqueUserSecret
 */
public interface UserSecret {

  /** Returns the type of user secret. */
  @Nonnull
  String getType();

  /** Returns the authorized roles that can use this secret. */
  @Nonnull
  Collection<String> getRoles();

  /**
   * Gets the value of this secret with the provided key and returns a string encoding of it. Secret
   * values that only have a binary encoding are not supported by this method.
   */
  @Nonnull
  String getSecretString(@Nonnull String key);

  /** Gets the value of this secret with the provided key and returns its raw bytes. */
  @Nonnull
  byte[] getSecretBytes(@Nonnull String key);
}
