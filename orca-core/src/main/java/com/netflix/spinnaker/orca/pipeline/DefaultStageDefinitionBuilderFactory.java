/*
 * Copyright 2017 Netflix, Inc.
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

package com.netflix.spinnaker.orca.pipeline;

import static java.util.stream.Collectors.toList;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import javax.annotation.Nonnull;
import com.netflix.spinnaker.orca.pipeline.ExecutionRunner.NoSuchStageDefinitionBuilder;
import com.netflix.spinnaker.orca.pipeline.model.Stage;

public class DefaultStageDefinitionBuilderFactory implements StageDefinitionBuilderFactory {
  private final Collection<StageDefinitionBuilder> stageDefinitionBuilders;

  public DefaultStageDefinitionBuilderFactory(Collection<StageDefinitionBuilder> stageDefinitionBuilders) {
    this.stageDefinitionBuilders = stageDefinitionBuilders;
  }

  public DefaultStageDefinitionBuilderFactory(StageDefinitionBuilder... stageDefinitionBuilders) {
    this(Arrays.asList(stageDefinitionBuilders));
  }

  @Override
  public @Nonnull StageDefinitionBuilder builderFor(
    @Nonnull Stage stage) throws NoSuchStageDefinitionBuilder {
    return stageDefinitionBuilders
      .stream()
      .filter((it) -> it.getType().equals(stage.getType()) || it.getType().equals(stage.getContext().get("alias")))
      .findFirst()
      .orElseThrow(() -> {
        List<String> knownTypes = stageDefinitionBuilders.stream().map(it -> it.getType()).sorted().collect(toList());
        return new NoSuchStageDefinitionBuilder(stage.getType(), knownTypes);
      });
  }
}
