package com.netflix.spinnaker.clouddriver.ecs.deploy.converters;

import com.netflix.spinnaker.clouddriver.deploy.DeployAtomicOperation;
import com.netflix.spinnaker.clouddriver.ecs.EcsOperation;
import com.netflix.spinnaker.clouddriver.ecs.deploy.description.BasicEcsDeployDescription;
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperation;
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperations;
import com.netflix.spinnaker.clouddriver.security.AbstractAtomicOperationsCredentialsSupport;
import org.springframework.stereotype.Component;

import java.util.Map;

@EcsOperation(AtomicOperations.CREATE_SERVER_GROUP)
@Component("basicEcsDeployDescription")
public class EcsCreateServerGroupAtomicOperationConverter extends AbstractAtomicOperationsCredentialsSupport {

  @Override
  public AtomicOperation convertOperation(Map input) {
    return new DeployAtomicOperation(convertDescription(input));
  }

  @Override
  public BasicEcsDeployDescription convertDescription(Map input) {
    BasicEcsDeployDescription converted = getObjectMapper().convertValue(input, BasicEcsDeployDescription.class);
    converted.setCredentials(getCredentialsObject(input.get("credentials").toString()));

    return converted;
  }
}
