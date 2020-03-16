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

package com.netflix.spinnaker.orca.api.simplestage;

import com.netflix.spinnaker.kork.annotations.Beta;
import org.pf4j.ExtensionPoint;

/**
 * Allows the creation of the most simple stage possible: One with a single task.
 *
 * @param <T> is a class plugin developers will create to have a concrete class that has all of the
 *     fields that are required for the stage to run.
 */
@Beta
public interface SimpleStage<T> extends ExtensionPoint {
  /**
   * When this stage runs, the execute method gets called. It takes in a class that is created that
   * has the data needed by the stage. It returns a class that contains the status of the stage,
   * outputs and context
   *
   * @param simpleStageInput
   * @return status, outputs, and context of the executed stage
   */
  SimpleStageOutput execute(SimpleStageInput<T> simpleStageInput);

  /** @return name of the stage */
  String getName();
}
