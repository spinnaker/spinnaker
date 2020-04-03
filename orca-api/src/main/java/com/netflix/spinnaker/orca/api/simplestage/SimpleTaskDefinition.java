/*
 * Copyright 2020 Armory, Inc.
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

import com.netflix.spinnaker.orca.api.pipeline.graph.TaskNode;
import com.netflix.spinnaker.orca.api.pipeline.graph.TaskNode.DefinedTask;
import javax.annotation.Nonnull;

/**
 * Provides a TaskDefinition for a SimpleStage which will be wrapped in a SimpleTask. SimpleTask
 * alone is not enough information to disambiguate the implementing class.
 */
public class SimpleTaskDefinition implements TaskNode, DefinedTask {
  private final String name;
  private final Class<? extends SimpleStage> implementingClass;

  /**
   * @param name The name of the SimpleStage that gets executed
   * @param implementingClass The SimpleStage class type that will be referenced by the task
   *     resolver
   */
  public SimpleTaskDefinition(
      @Nonnull String name, @Nonnull Class<? extends SimpleStage> implementingClass) {
    this.name = name;
    this.implementingClass = implementingClass;
  }

  @Override
  public @Nonnull String getName() {
    return name;
  }

  /** @return the SimpleStage class that will be executed by the task */
  public @Nonnull Class<? extends SimpleStage> getImplementingClass() {
    return implementingClass;
  }

  @Override
  public @Nonnull String getImplementingClassName() {
    return getImplementingClass().getCanonicalName();
  }
}
