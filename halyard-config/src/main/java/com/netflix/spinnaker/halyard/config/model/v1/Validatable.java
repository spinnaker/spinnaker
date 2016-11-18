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

import com.netflix.spinnaker.halyard.config.config.v1.HalconfigCoordinates;
import com.netflix.spinnaker.halyard.config.errors.v1.HalconfigProblem;

import java.util.List;

/**
 * Anything implementing this interface is validatable, meaning after we validate it, we get a non-null list of HalconfigProblems
 *
 * Warning: If any of the fields in a class implement Validatable, do not call validate on them!! This will mean even the simplest
 * config changes could cause the full halconfig for every deployment to be validated (very costly, many network calls). It is up
 * to whatever is performing a field update to decide what to validate.
 */
public interface Validatable {
  List<HalconfigProblem> validate(Halconfig context, HalconfigCoordinates coordinates);
}
