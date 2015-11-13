package com.netflix.spinnaker.kato.cf.deploy.converters

import com.netflix.spinnaker.clouddriver.cf.CloudFoundryOperation
import com.netflix.spinnaker.kato.cf.deploy.description.DestroyCloudFoundryServerGroupDescription
import com.netflix.spinnaker.kato.cf.deploy.ops.DestroyCloudFoundryServerGroupAtomicOperation
import com.netflix.spinnaker.kato.orchestration.AtomicOperation
import com.netflix.spinnaker.kato.orchestration.AtomicOperations
import com.netflix.spinnaker.kato.security.AbstractAtomicOperationsCredentialsSupport
import org.springframework.stereotype.Component

@CloudFoundryOperation(AtomicOperations.DESTROY_SERVER_GROUP)
@Component("destroyCloudFoundryServerGroupDescription")
class DestroyCloudFoundryServerGroupAtomicOperationConverter extends AbstractAtomicOperationsCredentialsSupport {

  @Override
  AtomicOperation convertOperation(Map input) {
    new DestroyCloudFoundryServerGroupAtomicOperation(convertDescription(input))
  }

  @Override
  Object convertDescription(Map input) {
    new DestroyCloudFoundryServerGroupDescription([
      serverGroupName: input.serverGroupName,
      zone: input.zone,
      credentials: getCredentialsObject(input.credentials as String)
    ])
  }
}
