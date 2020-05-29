/*
 * Copyright 2014 Netflix, Inc.
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
 *
 */
package com.netflix.spinnaker.front50.validator;

import com.netflix.spinnaker.front50.UntypedUtils;
import com.netflix.spinnaker.front50.model.application.Application;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nonnull;
import org.springframework.validation.AbstractErrors;
import org.springframework.validation.Errors;
import org.springframework.validation.FieldError;
import org.springframework.validation.ObjectError;

public class ApplicationValidationErrors extends AbstractErrors {
  public ApplicationValidationErrors(Application application) {
    this.application = application;
  }

  @Nonnull
  @Override
  public String getObjectName() {
    return application.getClass().getSimpleName();
  }

  @Override
  public void reject(@Nonnull String errorCode, Object[] errorArgs, String defaultMessage) {
    globalErrors.add(
        new ObjectError(getObjectName(), new String[] {errorCode}, errorArgs, defaultMessage));
  }

  @Override
  public void rejectValue(
      String field, @Nonnull String errorCode, Object[] errorArgs, String defaultMessage) {
    fieldErrors.add(
        new FieldError(
            getObjectName(),
            field,
            getFieldValue(field),
            false,
            new String[] {errorCode},
            errorArgs,
            defaultMessage));
  }

  @Override
  public void addAllErrors(Errors errors) {
    globalErrors.addAll(errors.getAllErrors());
  }

  @Override
  public Object getFieldValue(@Nonnull String field) {
    return UntypedUtils.getProperty(application, field);
  }

  public Application getApplication() {
    return application;
  }

  public void setApplication(Application application) {
    this.application = application;
  }

  @Nonnull
  @Override
  public List<ObjectError> getGlobalErrors() {
    return globalErrors;
  }

  public void setGlobalErrors(List<ObjectError> globalErrors) {
    this.globalErrors = globalErrors;
  }

  @Nonnull
  @Override
  public List<FieldError> getFieldErrors() {
    return fieldErrors;
  }

  public void setFieldErrors(List<FieldError> fieldErrors) {
    this.fieldErrors = fieldErrors;
  }

  private Application application;
  private List<ObjectError> globalErrors = new ArrayList<ObjectError>();
  private List<FieldError> fieldErrors = new ArrayList<FieldError>();
}
