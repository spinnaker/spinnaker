package com.netflix.kato.deploy.aws.converters

import com.netflix.kato.deploy.DeployAtomicOperation
import com.netflix.kato.deploy.aws.description.BasicAmazonDeployDescription
import com.netflix.kato.orchestration.AtomicOperation
import com.netflix.kato.security.AbstractAtomicOperationsCredentialsSupport
import org.springframework.stereotype.Component

@Component("basicAmazonDeployDescription")
class BasicAmazonDeployAtomicOperationConverter extends AbstractAtomicOperationsCredentialsSupport {
  AtomicOperation convertOperation(Map input) {
    new DeployAtomicOperation(convertDescription(input))
  }
  BasicAmazonDeployDescription convertDescription(Map input) {
    input.credentials = getCredentialsForEnvironment(input.credentials as String)
    new BasicAmazonDeployDescription(input)
  }
}
