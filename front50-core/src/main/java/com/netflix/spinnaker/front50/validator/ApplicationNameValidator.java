/*
 * Copyright 2020 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.front50.validator;

import com.google.common.base.Strings;
import com.netflix.spinnaker.front50.model.application.Application;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@EnableConfigurationProperties(ApplicationNameValidatorConfigurationProperties.class)
public class ApplicationNameValidator implements ApplicationValidator {
  private ApplicationNameValidatorConfigurationProperties properties;

  @Autowired
  public ApplicationNameValidator(ApplicationNameValidatorConfigurationProperties properties) {
    this.properties = properties;
  }

  @Override
  public void validate(Application application, ApplicationValidationErrors validationErrors) {
    if (Strings.isNullOrEmpty(properties.getValidationRegex())) {
      return;
    }

    String appName = Optional.ofNullable(application.getName()).orElse("");
    if (!appName.matches(properties.getValidationRegex())) {
      validationErrors.rejectValue(
          "name",
          "application.name.invalid",
          Optional.ofNullable(properties.getValidationMessage())
              .orElse(
                  "Application name doesn't satisfy the validation regex: "
                      + properties.getValidationRegex()));
    }
  }
}
