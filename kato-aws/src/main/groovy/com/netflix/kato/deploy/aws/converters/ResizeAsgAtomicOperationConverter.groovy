package com.netflix.kato.deploy.aws.converters

import com.netflix.kato.deploy.aws.description.ResizeAsgDescription
import com.netflix.kato.deploy.aws.ops.ResizeAsgAtomicOperation
import com.netflix.kato.orchestration.AtomicOperation
import com.netflix.kato.security.AbstractAtomicOperationsCredentialsSupport
import org.springframework.stereotype.Component

@Component("resizeAsgDescription")
class ResizeAsgAtomicOperationConverter extends AbstractAtomicOperationsCredentialsSupport {
  @Override
  AtomicOperation convertOperation(Map input) {
    new ResizeAsgAtomicOperation(convertDescription(input))
  }
  @Override
  ResizeAsgDescription convertDescription(Map input) {
    input.credentials = getCredentialsForEnvironment(input.credentials as String)
    new ResizeAsgDescription(input)
  }
}
