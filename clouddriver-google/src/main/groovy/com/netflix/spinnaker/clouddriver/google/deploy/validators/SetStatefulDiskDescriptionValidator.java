package com.netflix.spinnaker.clouddriver.google.deploy.validators;

import com.netflix.spinnaker.clouddriver.deploy.DescriptionValidator;
import com.netflix.spinnaker.clouddriver.google.GoogleOperation;
import com.netflix.spinnaker.clouddriver.google.deploy.description.SetStatefulDiskDescription;
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperations;
import java.util.List;
import org.springframework.stereotype.Component;
import org.springframework.validation.Errors;

@GoogleOperation(AtomicOperations.SET_STATEFUL_DISK)
@Component
public class SetStatefulDiskDescriptionValidator
    extends DescriptionValidator<SetStatefulDiskDescription> {

  @Override
  public void validate(
      List priorDescriptions, SetStatefulDiskDescription description, Errors errors) {
    StandardGceAttributeValidator helper =
        new StandardGceAttributeValidator("setStatefulDiskDescription", errors);
    helper.validateRegion(description.getRegion(), description.getCredentials());
    helper.validateServerGroupName(description.getServerGroupName());
    helper.validateName(description.getDeviceName(), "deviceName");
  }
}
