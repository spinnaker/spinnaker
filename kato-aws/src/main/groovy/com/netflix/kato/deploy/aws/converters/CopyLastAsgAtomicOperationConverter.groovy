package com.netflix.kato.deploy.aws.converters

import com.netflix.kato.deploy.aws.description.BasicAmazonDeployDescription
import com.netflix.kato.deploy.aws.ops.CopyLastAsgAtomicOperation
import com.netflix.kato.orchestration.AtomicOperation
import com.netflix.kato.security.AbstractAtomicOperationsCredentialsSupport
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.ApplicationContext
import org.springframework.stereotype.Component

@Component("copyLastAsgDescription")
class CopyLastAsgAtomicOperationConverter extends AbstractAtomicOperationsCredentialsSupport {
  @Autowired
  ApplicationContext applicationContext

  AtomicOperation convertOperation(Map input) {
    def op = new CopyLastAsgAtomicOperation(convertDescription(input))
    applicationContext?.autowireCapableBeanFactory?.autowireBean(op)
    op
  }

  BasicAmazonDeployDescription convertDescription(Map input) {
    input.credentials = getCredentialsForEnvironment(input.credentials as String)
    new BasicAmazonDeployDescription(input)
  }
}
