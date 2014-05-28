package com.netflix.spinnaker.kato.deploy.gce.converters

import com.netflix.spinnaker.kato.deploy.DeployAtomicOperation
import com.netflix.spinnaker.kato.deploy.gce.description.BasicGoogleDeployDescription
import com.netflix.spinnaker.kato.orchestration.AtomicOperation
import com.netflix.spinnaker.kato.security.AbstractAtomicOperationsCredentialsSupport
import org.springframework.stereotype.Component

@Component("basicGoogleDeployDescription")
class BasicGoogleDeployAtomicOperationConverter extends AbstractAtomicOperationsCredentialsSupport {
  AtomicOperation convertOperation(Map input) {
    new DeployAtomicOperation(convertDescription(input))
  }

  BasicGoogleDeployDescription convertDescription(Map input) {
    input.credentials = getCredentialsObject(input.credentials as String)
    new BasicGoogleDeployDescription(input)
  }
}
