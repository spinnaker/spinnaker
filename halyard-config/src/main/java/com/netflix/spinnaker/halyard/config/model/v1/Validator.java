/*
 * Copyright 2016 Google, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
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

package com.netflix.spinnaker.halyard.config.model.v1;

import com.netflix.spinnaker.halyard.config.model.v1.problem.ProblemSetBuilder;
import lombok.Getter;

import java.lang.reflect.Method;

/**
 * This abstract validator needs to be extended with validators of the form
 *
 * private void validateMyClass(MyClass m) { .. };
 *
 * Which will be dynamically dispatched by
 *
 * public void validate(Object o) { .. };
 *
 * The benefit of this approach is people can choose which type of validators to support without having to go around and
 * implement empty validators for each one. Furthermore, people can add new types of validators without having to provide
 * stubs for every other possible validatable asset.
 */
public abstract class Validator {
  /**
   * This needs to be abstract, and reimplemented in each class extending Validator because we need to call "getValidateMethod"
   * from the Validator implementing the method being searched for. Otherwise "this" always resolves to this abstract class.
   *
   * @param subject the subject of validation.
   */
  abstract public void validate(Object subject);

  @Getter
  private ProblemSetBuilder problemSetBuilder;

  /**
   * Recursively search the class hierarchy for the subjects validator. If none is found, we return null.
   *
   * @param subjectClass is the class of the subject to be validated.
   * @return the method performing validation on the provided class.
   */
  protected Method getValidateMethod(Class subjectClass) {
    if (subjectClass == Object.class) {
      return null;
    }

    Method result;
    String subjectName = subjectClass.getName();
    subjectName = subjectName.substring(subjectName.lastIndexOf('.') + 1);

    String methodName = "validate" + subjectName;

    try {
      result = getClass().getMethod(methodName, subjectClass);
    } catch (NoSuchMethodException ex) {
      result = getValidateMethod(subjectClass.getSuperclass());
    }

    return result;
  }
}
