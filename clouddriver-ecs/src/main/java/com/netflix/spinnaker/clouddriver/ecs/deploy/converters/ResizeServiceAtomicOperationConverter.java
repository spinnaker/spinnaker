package com.netflix.spinnaker.clouddriver.ecs.deploy.converters;

import com.netflix.spinnaker.clouddriver.ecs.EcsOperation;
import com.netflix.spinnaker.clouddriver.ecs.deploy.description.CloneServiceDescription;
import com.netflix.spinnaker.clouddriver.ecs.deploy.description.ResizeServiceDescription;
import com.netflix.spinnaker.clouddriver.ecs.deploy.ops.CloneServiceAtomicOperation;
import com.netflix.spinnaker.clouddriver.ecs.deploy.ops.ResizeServiceAtomicOperation;
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperation;
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperations;
import com.netflix.spinnaker.clouddriver.security.AbstractAtomicOperationsCredentialsSupport;
import org.springframework.stereotype.Component;

import java.util.Map;

@EcsOperation(AtomicOperations.RESIZE_SERVER_GROUP)
@Component("ecsResizeServerGroup")
public class ResizeServiceAtomicOperationConverter extends AbstractAtomicOperationsCredentialsSupport {

  @Override
  public AtomicOperation convertOperation(Map input) {
    return new ResizeServiceAtomicOperation(convertDescription(input));
  }

  @Override
  public ResizeServiceDescription convertDescription(Map input) {
    ResizeServiceDescription converted = getObjectMapper().convertValue(input, ResizeServiceDescription.class);
    converted.setCredentials(getCredentialsObject(input.get("credentials").toString()));

    return converted;
  }

}
