/*
 * Copyright 2014 Netflix, Inc.
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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

public class HasNameValidatorTest {
  private final HasNameValidator validator = new HasNameValidator();

  @ParameterizedTest(name = "Name validation: {0} should result in {1} error(s)")
  @MethodSource("nameProvider")
  @DisplayName("Name validation test")
  public void testNameValidation(String name, int numberOfErrors) {
    Application application = new Application();
    ApplicationValidationErrors errors = new ApplicationValidationErrors(application);

    application.setName(name);
    validator.validate(application, errors);

    assertEquals(
        numberOfErrors,
        errors.getAllErrors().size(),
        String.format("Expected %d error(s) for name: '%s'", numberOfErrors, name));
  }

  private static Stream<Object[]> nameProvider() {
    return Stream.of(
        new Object[] {null, 1},
        new Object[] {"", 1},
        new Object[] {" ", 1},
        new Object[] {"application", 0});
  }
}
