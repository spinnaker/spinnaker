package com.netflix.spinnaker.clouddriver.ecs.deploy.converters;

import com.netflix.spinnaker.clouddriver.ecs.EcsOperation;
import com.netflix.spinnaker.clouddriver.ecs.deploy.description.CloneServiceDescription;
import com.netflix.spinnaker.clouddriver.ecs.deploy.description.DeleteScalingPolicyDescription;
import com.netflix.spinnaker.clouddriver.ecs.deploy.ops.CloneServiceAtomicOperation;
import com.netflix.spinnaker.clouddriver.ecs.deploy.ops.DeleteScalingPolicyAtomicOperation;
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperation;
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperations;
import com.netflix.spinnaker.clouddriver.security.AbstractAtomicOperationsCredentialsSupport;
import org.springframework.stereotype.Component;

import java.util.Map;

@EcsOperation(AtomicOperations.DELETE_SCALING_POLICY)
@Component("ecsDeleteScalingPolicy")
public class DeleteScalingPolicyAtomicOperationConverter extends AbstractAtomicOperationsCredentialsSupport {

  @Override
  public AtomicOperation convertOperation(Map input) {
    return new DeleteScalingPolicyAtomicOperation(convertDescription(input));
  }

  @Override
  public DeleteScalingPolicyDescription convertDescription(Map input) {
    DeleteScalingPolicyDescription converted = getObjectMapper().convertValue(input, DeleteScalingPolicyDescription.class);
    converted.setCredentials(getCredentialsObject(input.get("credentials").toString()));

    return converted;
  }

}
