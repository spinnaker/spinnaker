package com.netflix.kato.deploy.aws

import com.netflix.kato.deploy.aws.description.BasicAmazonDeployDescription
import com.netflix.kato.deploy.aws.ops.CopyLastAsgAtomicOperation
import com.netflix.kato.orchestration.AtomicOperation
import com.netflix.kato.security.AbstractAtomicOperationsCredentialsSupport
import org.springframework.stereotype.Component

@Component("copyLastAsgDescription")
class CopyLastAsgAtomicOperationConverter extends AbstractAtomicOperationsCredentialsSupport {
  AtomicOperation convertOperation(Map input) {
    new CopyLastAsgAtomicOperation(convertDescription(input))
  }
  BasicAmazonDeployDescription convertDescription(Map input) {
    input.credentials = getCredentialsForEnvironment(input.credentials as String)
    new BasicAmazonDeployDescription(input)
  }
}
