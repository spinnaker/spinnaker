package com.netflix.spinnaker.front50.validator;

import com.netflix.spinnaker.front50.model.application.Application;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
public class HasNameValidator implements ApplicationValidator {
  @Override
  public void validate(Application application, ApplicationValidationErrors validationErrors) {
    if (Optional.ofNullable(application.getName()).orElse("").trim().isEmpty()) {
      validationErrors.rejectValue(
          "name", "application.name.empty", "Application must have a non-empty name");
    }
  }
}
