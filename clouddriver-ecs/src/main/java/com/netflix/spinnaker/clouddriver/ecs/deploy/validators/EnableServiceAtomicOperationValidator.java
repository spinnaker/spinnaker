package com.netflix.spinnaker.clouddriver.ecs.deploy.validators;

import com.netflix.spinnaker.clouddriver.deploy.DescriptionValidator;
import com.netflix.spinnaker.clouddriver.ecs.EcsOperation;
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperations;
import org.springframework.stereotype.Component;
import org.springframework.validation.Errors;

import java.util.List;

@EcsOperation(AtomicOperations.ENABLE_SERVER_GROUP)
@Component("enableServiceAtomicOperationValidator")
public class EnableServiceAtomicOperationValidator extends DescriptionValidator {

  @Override
  public void validate(List priorDescriptions, Object description, Errors errors) {

    // TODO - Implement this stub

  }
}
