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

package com.netflix.spinnaker.clouddriver.deploy;

import com.netflix.spinnaker.kork.annotations.Beta;
import com.netflix.spinnaker.kork.plugins.api.internal.SpinnakerExtensionPoint;
import java.util.List;

/**
 * An extension point for adding additional validators for AtomicOperations. Can be used globally to
 * add the same validation to all operations or can be narrowed to a subset of operations.
 *
 * <p>{@code OperationsService} calls out to {@code AnnotationsBasedAtomicOperationsRegistry} to
 * fetch the validator for each atomic operation description. {@code
 * AnnotationsBasedAtomicOperationsRegistry} contains an autowired List of {@code
 * GlobalDescriptionValidator}s, which will pull in any bean that implements this interface. It then
 * returns a {@code CompositeDescriptionValidator} to that will call {@code validate()} on the
 * original atomic operation description validator and all {@code GlobalDescriptionValidator}s.
 */
@Beta
public interface GlobalDescriptionValidator extends SpinnakerExtensionPoint {
  /**
   * Whether or not the validator should be applied to the passed-in {@code OperationDescription}.
   * This should return true if the validator should be applied to all operations.
   *
   * @return true if this validator should be applied, false otherwise
   */
  <T> boolean handles(T description);

  /**
   * Validates the {@code description} and adds any validation errors to the {@code errors}
   * parameter.
   */
  <T> void validate(
      String operationName, List<T> priorDescriptions, T description, ValidationErrors errors);
}
