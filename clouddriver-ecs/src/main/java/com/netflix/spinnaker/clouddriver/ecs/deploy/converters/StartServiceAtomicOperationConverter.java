package com.netflix.spinnaker.clouddriver.ecs.deploy.converters;

import com.netflix.spinnaker.clouddriver.ecs.EcsOperation;
import com.netflix.spinnaker.clouddriver.ecs.deploy.description.CloneServiceDescription;
import com.netflix.spinnaker.clouddriver.ecs.deploy.description.StartServiceDescription;
import com.netflix.spinnaker.clouddriver.ecs.deploy.ops.CloneServiceAtomicOperation;
import com.netflix.spinnaker.clouddriver.ecs.deploy.ops.StartServiceAtomicOperation;
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperation;
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperations;
import com.netflix.spinnaker.clouddriver.security.AbstractAtomicOperationsCredentialsSupport;
import org.springframework.stereotype.Component;

import java.util.Map;

@EcsOperation(AtomicOperations.START_SERVER_GROUP)
@Component("ecsStartServerGroup")
public class StartServiceAtomicOperationConverter extends AbstractAtomicOperationsCredentialsSupport {

  @Override
  public AtomicOperation convertOperation(Map input) {
    return new StartServiceAtomicOperation(convertDescription(input));
  }

  @Override
  public StartServiceDescription convertDescription(Map input) {
    StartServiceDescription converted = getObjectMapper().convertValue(input, StartServiceDescription.class);
    converted.setCredentials(getCredentialsObject(input.get("credentials").toString()));

    return converted;
  }

}
