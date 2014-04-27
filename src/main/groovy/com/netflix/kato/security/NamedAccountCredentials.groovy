package com.netflix.kato.security

/**
 * Implementations of this interface will provide credentials for use in the {@link NamedAccountCredentialsHolder}.
 *
 * @param parameterized type of the credentials object returned.
 * @author Dan Woods
 */
public interface NamedAccountCredentials<T> {
  /**
   * This method will return the credentials object.
   *
   * @return credentials object
   */
  T getCredentials()
}
