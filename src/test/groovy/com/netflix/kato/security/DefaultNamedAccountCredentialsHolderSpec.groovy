package com.netflix.kato.security

import spock.lang.Shared
import spock.lang.Specification

class DefaultNamedAccountCredentialsHolderSpec extends Specification {

  @Shared
  DefaultNamedAccountCredentialsHolder credentialsHolder

  def setup() {
    this.credentialsHolder = new DefaultNamedAccountCredentialsHolder()
  }

  void "credentials are able to be saved and retrieved"() {
    setup:
      def credentials = Mock(NamedAccountCredentials)
      credentialsHolder.put("test", credentials)

    when:
      def c1 = credentialsHolder.getCredentials("test")

    then:
      c1.is credentials
  }

  void "credential names are retrievable"() {
    setup:
      def credentials = Mock(NamedAccountCredentials)
      credentialsHolder.put("test", credentials)

    when:
      def l = credentialsHolder.accountNames

    then:
      l == ["test"]
  }
}
