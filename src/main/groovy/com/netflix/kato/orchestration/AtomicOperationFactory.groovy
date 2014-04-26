package com.netflix.kato.orchestration

import com.netflix.kato.deploy.DeployAtomicOperation
import com.netflix.kato.deploy.aws.description.BasicAmazonDeployDescription
import com.netflix.kato.deploy.aws.description.ShrinkClusterDescription
import com.netflix.kato.deploy.aws.ops.CopyLastAsgAtomicOperation
import com.netflix.kato.deploy.aws.ops.ShrinkClusterAtomicOperation
import com.netflix.kato.security.NamedAccountCredentialsHolder


import static com.netflix.kato.holders.ApplicationContextHolder.applicationContext

enum AtomicOperationFactory implements AtomicOperationConverter {
  basicAmazonDeployDescription {
    AtomicOperation convertOperation(Map input) {
      new DeployAtomicOperation(convertDescription(input))
    }
    BasicAmazonDeployDescription convertDescription(Map input) {
      input.credentials = getCredentialsForEnvironment(input.credentials)
      new BasicAmazonDeployDescription(input)
    }
  },
  copyLastAsgDeployDescription {
    AtomicOperation convertOperation(Map input) {
      new CopyLastAsgAtomicOperation(convertDescription(input))
    }
    BasicAmazonDeployDescription convertDescription(Map input) {
      input.credentials = getCredentialsForEnvironment(input.credentials)
      new BasicAmazonDeployDescription(input)
    }
  },
  shrinkClusterDescription {
    AtomicOperation convertOperation(Map input) {
      new ShrinkClusterAtomicOperation(convertDescription(input))
    }
    ShrinkClusterDescription convertDescription(Map input) {
      input.credentials = getCredentialsForEnvironment(input.credentials)
      new ShrinkClusterDescription(input)
    }
  }

  static def getCredentialsForEnvironment(environment) {
    namedAccountCredentialsHolder.getCredentials(environment as String).credentials
  }

  static NamedAccountCredentialsHolder getNamedAccountCredentialsHolder() {
    applicationContext.getBean(NamedAccountCredentialsHolder)
  }

  @Override
  AtomicOperation convertOperation(Map input) {
    throw new UnsupportedOperationException()
  }

  @Override
  def convertDescription(Map input) {
    throw new UnsupportedOperationException()
  }
}
