package com.netflix.spinnaker.clouddriver.ecs.deploy.converters;

import com.netflix.spinnaker.clouddriver.ecs.EcsOperation;
import com.netflix.spinnaker.clouddriver.ecs.deploy.description.UpdateServiceAndTaskConfigDescription;
import com.netflix.spinnaker.clouddriver.ecs.deploy.ops.UpdateServiceAndTaskConfigAtomicOperation;
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperation;
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperations;
import com.netflix.spinnaker.clouddriver.security.AbstractAtomicOperationsCredentialsSupport;
import org.springframework.stereotype.Component;

import java.util.Map;

@EcsOperation(AtomicOperations.UPDATE_LAUNCH_CONFIG)
@Component("ecsUpdateServiceAndTaskConfig")
public class UpdateServiceAndTaskConfigAtomicOperationConverter extends AbstractAtomicOperationsCredentialsSupport {

  @Override
  public AtomicOperation convertOperation(Map input) {
    return new UpdateServiceAndTaskConfigAtomicOperation(convertDescription(input));
  }

  @Override
  public UpdateServiceAndTaskConfigDescription convertDescription(Map input) {
    UpdateServiceAndTaskConfigDescription converted = getObjectMapper().convertValue(input, UpdateServiceAndTaskConfigDescription.class);
    converted.setCredentials(getCredentialsObject(input.get("credentials").toString()));

    return converted;
  }

}
