package com.netflix.spinnaker.front50.validator;

import com.netflix.spinnaker.front50.model.application.Application;

public interface ApplicationValidator {
  void validate(Application application, ApplicationValidationErrors validationErrors);
}
