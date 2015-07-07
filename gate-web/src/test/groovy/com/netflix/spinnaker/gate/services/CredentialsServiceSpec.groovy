package com.netflix.spinnaker.gate.services

import com.netflix.spinnaker.gate.services.internal.KatoService
import com.netflix.spinnaker.security.AuthenticatedRequest
import com.netflix.spinnaker.security.User
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Unroll

class CredentialsServiceSpec extends Specification {
  @Shared
  List<KatoService.Account> accounts = [
    new KatoService.Account(name: "account1", type: "aws"),
    new KatoService.Account(name: "account2", type: "aws")
  ]

  KatoService katoService = Mock(KatoService) {
    1 * getAccounts() >> { accounts }
    0 * _
  }

  void "should return all accounts if no authenticated user"() {
    expect:
    new CredentialsService(katoService: katoService).getAccounts() == accounts
  }

  @Unroll
  void "should filter accounts based on authenticated user"() {
    expect:
    AuthenticatedRequest.propagate({
      new CredentialsService(katoService: katoService).getAccounts()
    }, false, new User("email", null, null, [], userAccounts)).call() as List<KatoService.Account> == allowedACcounts

    where:
    userAccounts                         || allowedACcounts
    ["account1"]                         || [accounts[0]]
    ["account2"]                         || [accounts[1]]
    ["account1", "account2"]             || accounts
    ["account1", "account2", "account3"] || accounts
    []                                   || []
    null                                 || []
  }
}
