package com.netflix.kato.security

/**
 * An implementation of this interface will provide a correlation of a string name to a {@link NamedAccountCredentials}
 * object, which can be used to credentials by an arbitrary name.
 *
 * @author Dan Woods
 */
public interface NamedAccountCredentialsHolder {
  /**
   * Retrieves a {@link NamedAccountCredentials} object by the supplied name.
   *
   * @param name
   * @return a NamedAccountCredentials object.
   */
  NamedAccountCredentials getCredentials(String name)

  /**
   * Provides callers with a list of the available names in the holder's internal repository.
   *
   * @return list of account names
   */
  List<String> getAccountNames()

  /**
   * Stores a {@link NamedAccountCredentials} object in the holder's internal repository, keyed on the supplied name.
   *
   * @param name
   * @param namedAccountCredentials
   */
  void put(String name, NamedAccountCredentials namedAccountCredentials)
}