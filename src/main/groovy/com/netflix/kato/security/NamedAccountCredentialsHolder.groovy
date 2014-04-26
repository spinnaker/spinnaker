package com.netflix.kato.security

public interface NamedAccountCredentialsHolder {
  NamedAccountCredentials getCredentials(String name)
  List<String> getAccountNames()
  void put(String name, NamedAccountCredentials namedAccountCredentials)
}