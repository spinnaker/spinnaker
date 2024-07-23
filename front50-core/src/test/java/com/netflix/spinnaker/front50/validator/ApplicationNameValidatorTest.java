/*
 * Copyright 2020 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.front50.validator;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.netflix.spinnaker.front50.model.application.Application;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

public class ApplicationNameValidatorTest {

  @ParameterizedTest(name = "validates {0} isValid: {1}")
  @MethodSource("appNameProvider")
  @DisplayName("AppName validation test")
  public void testAppNameValidation(String appName, boolean isValid) {
    ApplicationNameValidatorConfigurationProperties properties =
        new ApplicationNameValidatorConfigurationProperties();
    properties.setValidationRegex("^[a-zA-Z0-9.\\-_]*$");
    ApplicationNameValidator validator = new ApplicationNameValidator(properties);
    Application application = new Application();
    application.setName(appName);
    ApplicationValidationErrors errors = new ApplicationValidationErrors(application);

    validator.validate(application, errors);

    assertEquals(isValid ? 0 : 1, errors.getAllErrors().size());
  }

  @Test
  void doesNotValidateWhenNoRegexSupplied() {
    Application application = new Application();
    application.setName("søme wéird name!");
    ApplicationValidationErrors errors = new ApplicationValidationErrors(application);

    ApplicationNameValidatorConfigurationProperties properties =
        new ApplicationNameValidatorConfigurationProperties();
    properties.setValidationRegex("");
    ApplicationNameValidator validator = new ApplicationNameValidator(properties);
    validator.validate(application, errors);

    assertEquals(0, errors.getAllErrors().size());

    validator = new ApplicationNameValidator(new ApplicationNameValidatorConfigurationProperties());
    validator.validate(application, errors);

    assertEquals(0, errors.getAllErrors().size());
  }

  @Test
  void usesOptionalErrorMessage() {
    Application application = new Application();
    application.setName("noncompliantname!");
    ApplicationValidationErrors errors = new ApplicationValidationErrors(application);

    ApplicationNameValidatorConfigurationProperties properties =
        new ApplicationNameValidatorConfigurationProperties();
    properties.setValidationRegex("strictname");
    ApplicationNameValidator validator = new ApplicationNameValidator(properties);
    validator.validate(application, errors);

    assertEquals(1, errors.getAllErrors().size());
    assertEquals(
        "Application name doesn't satisfy the validation regex: " + properties.getValidationRegex(),
        errors.getAllErrors().get(0).getDefaultMessage());

    errors = new ApplicationValidationErrors(application);
    properties.setValidationMessage("a validation message");
    validator.validate(application, errors);

    assertEquals(1, errors.getAllErrors().size());
    assertEquals(
        properties.getValidationMessage(), errors.getAllErrors().get(0).getDefaultMessage());
  }

  private static Stream<Object[]> appNameProvider() {
    return Stream.of(
        new Object[] {"validname", true},
        new Object[] {"valid1name", true},
        new Object[] {"valid-name", true},
        new Object[] {"valid1.name", true},
        new Object[] {"valid-1_name", true},
        new Object[] {"invalid!name", false},
        new Object[] {"invalid name", false},
        new Object[] {"invalid.имя", false});
  }
}
