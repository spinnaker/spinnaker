/*
 * Copyright 2019 Armory, Inc.
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

package com.netflix.spinnaker.orca.pipeline;

import com.netflix.spinnaker.orca.api.SimpleStage;
import com.netflix.spinnaker.orca.pipeline.model.Stage;
import javax.annotation.Nonnull;

public class SimpleStageDefinitionBuilder implements StageDefinitionBuilder {
  private SimpleStage simpleStage;

  public SimpleStageDefinitionBuilder(SimpleStage simpleStage) {
    this.simpleStage = simpleStage;
  }

  public void taskGraph(@Nonnull Stage stage, @Nonnull TaskNode.Builder builder) {
    SimpleTask task = new SimpleTask(simpleStage);
    builder.withTask(simpleStage.getName(), task.getClass());
  }
}
