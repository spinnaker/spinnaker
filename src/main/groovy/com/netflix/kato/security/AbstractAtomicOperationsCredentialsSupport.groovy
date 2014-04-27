package com.netflix.kato.security

import com.netflix.kato.orchestration.AtomicOperationConverter
import org.springframework.beans.factory.annotation.Autowired

abstract class AbstractAtomicOperationsCredentialsSupport implements AtomicOperationConverter {
  @Autowired
  NamedAccountCredentialsHolder namedAccountCredentialsHolder

  def getCredentialsForEnvironment(String name) {
    namedAccountCredentialsHolder.getCredentials(name).credentials
  }
}
