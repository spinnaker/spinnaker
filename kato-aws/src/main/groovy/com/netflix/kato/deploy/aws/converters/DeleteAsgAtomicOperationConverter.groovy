package com.netflix.kato.deploy.aws.converters

import com.netflix.kato.deploy.aws.description.DeleteAsgDescription
import com.netflix.kato.deploy.aws.ops.DeleteAsgAtomicOperation
import com.netflix.kato.orchestration.AtomicOperation
import com.netflix.kato.security.AbstractAtomicOperationsCredentialsSupport
import org.springframework.stereotype.Component

@Component("deleteAsgDescription")
class DeleteAsgAtomicOperationConverter extends AbstractAtomicOperationsCredentialsSupport {
  AtomicOperation convertOperation(Map input) {
    new DeleteAsgAtomicOperation(convertDescription(input))
  }
  DeleteAsgDescription convertDescription(Map input) {
    input.credentials = getCredentialsForEnvironment(input.credentials as String)
    new DeleteAsgDescription(input)
  }
}
