/*
 * Copyright 2021 Armory, Inc.
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

package com.netflix.spinnaker.clouddriver.orchestration;

import com.netflix.spinnaker.clouddriver.deploy.DescriptionValidator;
import com.netflix.spinnaker.clouddriver.deploy.GlobalDescriptionValidator;
import com.netflix.spinnaker.clouddriver.deploy.ValidationErrors;
import java.util.List;
import java.util.Optional;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class CompositeDescriptionValidator<T> extends DescriptionValidator<T> {

  private final String operationName;
  private final String cloudProvider;
  @Getter private final DescriptionValidator<T> validator;
  private final List<GlobalDescriptionValidator> globalValidators;

  public CompositeDescriptionValidator(
      String operationName,
      String cloudProvider,
      DescriptionValidator<T> validator,
      List<GlobalDescriptionValidator> extensibleValidators) {
    this.operationName = operationName;
    this.cloudProvider = cloudProvider;
    this.validator = validator;
    this.globalValidators = extensibleValidators;
  }

  @Override
  public void validate(List<T> priorDescriptions, T description, ValidationErrors errors) {
    if (globalValidators != null) {
      globalValidators.forEach(
          v -> {
            if (v.handles(description)) {
              v.validate(operationName, priorDescriptions, description, errors);
            }
          });
    }
    if (validator == null) {
      String operationName =
          Optional.ofNullable(description)
              .map(it -> it.getClass().getSimpleName())
              .orElse("UNKNOWN");
      log.warn(
          String.format(
              "No validator found for operation %s and cloud provider %s",
              operationName, cloudProvider));
    } else {
      validator.validate(priorDescriptions, description, errors);
    }
  }
}
