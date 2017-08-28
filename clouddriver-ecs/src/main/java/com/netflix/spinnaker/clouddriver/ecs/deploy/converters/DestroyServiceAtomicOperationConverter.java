package com.netflix.spinnaker.clouddriver.ecs.deploy.converters;

import com.netflix.spinnaker.clouddriver.ecs.EcsOperation;
import com.netflix.spinnaker.clouddriver.ecs.deploy.description.CloneServiceDescription;
import com.netflix.spinnaker.clouddriver.ecs.deploy.description.DestroyServiceDescription;
import com.netflix.spinnaker.clouddriver.ecs.deploy.ops.CloneServiceAtomicOperation;
import com.netflix.spinnaker.clouddriver.ecs.deploy.ops.DestroyServiceAtomicOperation;
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperation;
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperations;
import com.netflix.spinnaker.clouddriver.security.AbstractAtomicOperationsCredentialsSupport;
import org.springframework.stereotype.Component;

import java.util.Map;

@EcsOperation(AtomicOperations.DESTROY_SERVER_GROUP)
@Component("ecsDestroyServerGroup")
public class DestroyServiceAtomicOperationConverter extends AbstractAtomicOperationsCredentialsSupport {

  @Override
  public AtomicOperation convertOperation(Map input) {
    return new DestroyServiceAtomicOperation(convertDescription(input));
  }

  @Override
  public DestroyServiceDescription convertDescription(Map input) {
    DestroyServiceDescription converted = getObjectMapper().convertValue(input, DestroyServiceDescription.class);
    converted.setCredentials(getCredentialsObject(input.get("credentials").toString()));

    return converted;
  }

}
