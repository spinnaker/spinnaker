/*
 * Copyright 2019 Alibaba Group.
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
package com.netflix.spinnaker.orca.kato.pipeline;

import com.netflix.spinnaker.orca.kato.tasks.ModifyScalingGroupTask;
import com.netflix.spinnaker.orca.pipeline.StageDefinitionBuilder;
import com.netflix.spinnaker.orca.pipeline.TaskNode.Builder;
import com.netflix.spinnaker.orca.pipeline.model.Stage;
import groovy.transform.CompileStatic;
import javax.annotation.Nonnull;
import org.springframework.stereotype.Component;

@Component
@CompileStatic
public class ModifyScalingGroupStage implements StageDefinitionBuilder {

  @Override
  public void taskGraph(@Nonnull Stage stage, @Nonnull Builder builder) {
    builder.withTask("modifyScalingGroup", ModifyScalingGroupTask.class);
  }
}
