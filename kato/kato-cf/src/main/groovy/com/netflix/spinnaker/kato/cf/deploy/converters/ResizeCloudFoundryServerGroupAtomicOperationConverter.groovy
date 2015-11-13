package com.netflix.spinnaker.kato.cf.deploy.converters
import com.netflix.spinnaker.clouddriver.cf.CloudFoundryOperation
import com.netflix.spinnaker.kato.cf.deploy.description.ResizeCloudFoundryServerGroupDescription
import com.netflix.spinnaker.kato.cf.deploy.ops.ResizeCloudFoundryServerGroupAtomicOperation
import com.netflix.spinnaker.kato.orchestration.AtomicOperation
import com.netflix.spinnaker.kato.orchestration.AtomicOperations
import com.netflix.spinnaker.kato.security.AbstractAtomicOperationsCredentialsSupport
import org.springframework.stereotype.Component

@CloudFoundryOperation(AtomicOperations.RESIZE_SERVER_GROUP)
@Component("resizeCloudFoundryServerGroupDescription")
class ResizeCloudFoundryServerGroupAtomicOperationConverter extends AbstractAtomicOperationsCredentialsSupport {

  @Override
  AtomicOperation convertOperation(Map input) {
    new ResizeCloudFoundryServerGroupAtomicOperation(convertDescription(input))
  }

  @Override
  Object convertDescription(Map input) {
    new ResizeCloudFoundryServerGroupDescription([
        serverGroupName: input.serverGroupName,
        zone: input.zone,
        targetSize: input.targetSize,
        credentials: getCredentialsObject(input.credentials as String)
    ])
  }
}
