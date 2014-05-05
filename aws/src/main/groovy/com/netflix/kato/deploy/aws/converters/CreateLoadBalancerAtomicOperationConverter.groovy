package com.netflix.kato.deploy.aws.converters

import com.netflix.kato.deploy.aws.description.CreateLoadBalancerDescription
import com.netflix.kato.deploy.aws.ops.loadbalancer.CreateLoadBalancerAtomicOperation
import com.netflix.kato.orchestration.AtomicOperationConverter
import org.springframework.stereotype.Component

@Component("createLoadBalancerDescription")
class CreateLoadBalancerAtomicOperationConverter implements AtomicOperationConverter {
  @Override
  CreateLoadBalancerAtomicOperation convertOperation(Map input) {
    new CreateLoadBalancerAtomicOperation(convertDescription(input))
  }

  @Override
  CreateLoadBalancerDescription convertDescription(Map input) {
    new CreateLoadBalancerDescription(input)
  }
}
