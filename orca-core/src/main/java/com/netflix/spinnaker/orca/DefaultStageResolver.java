/*
 * Copyright 2019 Netflix, Inc.
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

package com.netflix.spinnaker.orca;

import static java.lang.String.format;

import com.netflix.spinnaker.orca.api.pipeline.graph.StageDefinitionBuilder;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import javax.annotation.Nonnull;

/**
 * {@code StageResolver} allows for {@code StageDefinitionBuilder} retrieval via bean name or alias.
 *
 * <p>Aliases represent the previous bean names that a {@code StageDefinitionBuilder} registered as.
 */
public class DefaultStageResolver implements StageResolver {
  private final Map<String, StageDefinitionBuilder> stageDefinitionBuilderByAlias = new HashMap<>();

  public DefaultStageResolver(Collection<StageDefinitionBuilder> stageDefinitionBuilders) {
    for (StageDefinitionBuilder stageDefinitionBuilder : stageDefinitionBuilders) {
      stageDefinitionBuilderByAlias.put(stageDefinitionBuilder.getType(), stageDefinitionBuilder);
      for (String alias : stageDefinitionBuilder.aliases()) {
        if (stageDefinitionBuilderByAlias.containsKey(alias)) {
          throw new DuplicateStageAliasException(
              format(
                  "Duplicate stage alias detected (alias: %s, previous: %s, current: %s)",
                  alias,
                  stageDefinitionBuilderByAlias.get(alias).getClass().getCanonicalName(),
                  stageDefinitionBuilder.getClass().getCanonicalName()));
        }

        stageDefinitionBuilderByAlias.put(alias, stageDefinitionBuilder);
      }
    }
  }

  /**
   * Fetch a {@code StageDefinitionBuilder} by {@code type} or {@code typeAlias}.
   *
   * @param type StageDefinitionBuilder type
   * @param typeAlias StageDefinitionBuilder alias (optional)
   * @return the StageDefinitionBuilder matching {@code type} or {@code typeAlias}
   * @throws NoSuchStageDefinitionBuilderException if StageDefinitionBuilder does not exist
   */
  @Override
  @Nonnull
  public StageDefinitionBuilder getStageDefinitionBuilder(@Nonnull String type, String typeAlias) {
    StageDefinitionBuilder stageDefinitionBuilder =
        stageDefinitionBuilderByAlias.getOrDefault(
            type, stageDefinitionBuilderByAlias.get(typeAlias));

    if (stageDefinitionBuilder == null) {
      throw new NoSuchStageDefinitionBuilderException(
          type, typeAlias, stageDefinitionBuilderByAlias.keySet());
    }

    return stageDefinitionBuilder;
  }
}
