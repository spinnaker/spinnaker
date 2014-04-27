package com.netflix.kato.security

import java.util.concurrent.ConcurrentHashMap

class DefaultNamedAccountCredentialsHolder implements NamedAccountCredentialsHolder {
  private static final Map<String, NamedAccountCredentials> accountCredentials = new ConcurrentHashMap<>()

  @Override
  void put(String name, NamedAccountCredentials namedAccountCredentials) {
    accountCredentials.put name, namedAccountCredentials
  }

  @Override
  NamedAccountCredentials getCredentials(String name) {
    accountCredentials.get name
  }

  @Override
  List<String> getAccountNames() {
    accountCredentials.keySet() as List<String>
  }
}
