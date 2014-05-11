package com.netflix.kato.deploy.gce.converters

import com.netflix.kato.deploy.gce.description.BasicGoogleDeployDescription
import com.netflix.kato.deploy.DeployAtomicOperation
import com.netflix.kato.orchestration.AtomicOperation
import com.netflix.kato.security.AbstractAtomicOperationsCredentialsSupport
import org.springframework.stereotype.Component

@Component("basicGoogleDeployDescription")
class BasicGoogleDeployAtomicOperationConverter extends AbstractAtomicOperationsCredentialsSupport {
  AtomicOperation convertOperation(Map input) {
    new DeployAtomicOperation(convertDescription(input))
  }

  BasicGoogleDeployDescription convertDescription(Map input) {
    input.credentials = getCredentialsForEnvironment(input.credentials as String)
    new BasicGoogleDeployDescription(input)
  }
}
