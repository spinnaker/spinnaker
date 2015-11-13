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

package com.netflix.spinnaker.kato.deploy

import org.springframework.validation.AbstractErrors
import org.springframework.validation.Errors
import org.springframework.validation.FieldError
import org.springframework.validation.ObjectError

class DescriptionValidationErrors extends AbstractErrors {
  Object description
  List<ObjectError> globalErrors = new ArrayList<ObjectError>();
  List<FieldError> fieldErrors = new ArrayList<FieldError>();

  DescriptionValidationErrors(Object description) {
    this.description = description
  }

  @Override
  String getObjectName() {
    description.class.simpleName
  }

  @Override
  void reject(String errorCode, Object[] errorArgs, String defaultMessage) {
    globalErrors.add(new ObjectError(objectName, [errorCode] as String[], errorArgs, defaultMessage))
  }

  @Override
  void rejectValue(String field, String errorCode, Object[] errorArgs, String defaultMessage) {
    fieldErrors.add(new FieldError(objectName, field, null, false, [errorCode] as String[], errorArgs, defaultMessage))
  }

  @Override
  void addAllErrors(Errors errors) {
    globalErrors.addAll errors.allErrors
  }

  @Override
  Object getFieldValue(String field) {
    description."$field"
  }
}
