package com.netflix.kato.deploy.aws.converters

import com.netflix.kato.deploy.aws.description.ShrinkClusterDescription
import com.netflix.kato.deploy.aws.ops.ShrinkClusterAtomicOperation
import com.netflix.kato.orchestration.AtomicOperation
import com.netflix.kato.security.AbstractAtomicOperationsCredentialsSupport
import org.springframework.stereotype.Component

@Component("shrinkClusterDescription")
class ShrinkClusterAtomicOperationConverter extends AbstractAtomicOperationsCredentialsSupport {
  AtomicOperation convertOperation(Map input) {
    new ShrinkClusterAtomicOperation(convertDescription(input))
  }
  ShrinkClusterDescription convertDescription(Map input) {
    input.credentials = getCredentialsForEnvironment(input.credentials as String)
    new ShrinkClusterDescription(input)
  }
}
