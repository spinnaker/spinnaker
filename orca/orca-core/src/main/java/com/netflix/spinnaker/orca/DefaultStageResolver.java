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
import java.util.ArrayList;
import java.util.Collection;
import java.util.concurrent.ConcurrentHashMap;
import javax.annotation.Nonnull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;

/**
 * {@code StageResolver} allows for {@code StageDefinitionBuilder} retrieval via bean name or alias.
 *
 * <p>Aliases represent the previous bean names that a {@code StageDefinitionBuilder} registered as.
 */
public class DefaultStageResolver implements StageResolver {
  private final ConcurrentHashMap<String, StageDefinitionBuilder> stageDefinitionBuilderByAlias =
      new ConcurrentHashMap<>();
  private final ObjectProvider<Collection<StageDefinitionBuilder>> stageDefinitionBuildersProvider;

  private final Logger log = LoggerFactory.getLogger(getClass());

  public DefaultStageResolver(
      ObjectProvider<Collection<StageDefinitionBuilder>> stageDefinitionBuildersProvider) {
    this.stageDefinitionBuildersProvider = stageDefinitionBuildersProvider;
    Collection<StageDefinitionBuilder> stageDefinitionBuilders =
        stageDefinitionBuildersProvider.getIfAvailable(ArrayList::new);
    for (StageDefinitionBuilder stageDefinitionBuilder : stageDefinitionBuilders) {
      stageDefinitionBuilderByAlias.put(stageDefinitionBuilder.getType(), stageDefinitionBuilder);
      addAliases(stageDefinitionBuilder);
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
    StageDefinitionBuilder stageDefinitionBuilder = getOrDefault(type, typeAlias);

    if (stageDefinitionBuilder == null) {
      log.debug(
          "Stage definition builder for '{}' not found in initial stage definition builder cache, fetching missing stage from stage provider.",
          type);
      addMissingStagesFromStageProvider();
      stageDefinitionBuilder = getOrDefault(type, typeAlias);

      if (stageDefinitionBuilder == null) {
        throw new NoSuchStageDefinitionBuilderException(
            type, typeAlias, stageDefinitionBuilderByAlias.keySet());
      }
    }

    return stageDefinitionBuilder;
  }

  /**
   * {@link ConcurrentHashMap#get)} throws an NPE if the parameter is null, so first check if
   * typeAlias is null and then perform the necessary lookups.
   */
  private StageDefinitionBuilder getOrDefault(@Nonnull String type, String typeAlias) {
    StageDefinitionBuilder stageDefinitionBuilder = null;
    if (typeAlias == null) {
      stageDefinitionBuilder = stageDefinitionBuilderByAlias.get(type);
    }

    if (stageDefinitionBuilder == null && typeAlias != null) {
      stageDefinitionBuilder = stageDefinitionBuilderByAlias.get(typeAlias);
    }

    return stageDefinitionBuilder;
  }

  /**
   * Lookup stages from the stage provider and add missing stages to stageDefinitionBuilderByAlias
   * cache.
   */
  private void addMissingStagesFromStageProvider() {
    for (StageDefinitionBuilder stageDefinitionBuilder :
        stageDefinitionBuildersProvider.getIfAvailable(ArrayList::new)) {
      if (stageDefinitionBuilderByAlias.get(stageDefinitionBuilder.getType()) == null) {
        stageDefinitionBuilderByAlias.put(stageDefinitionBuilder.getType(), stageDefinitionBuilder);
        addAliases(stageDefinitionBuilder);
        log.info(
            "{} stage resolved from stage provider and added to cache",
            stageDefinitionBuilder.getType());
      }
    }
  }

  private void addAliases(StageDefinitionBuilder stageDefinitionBuilder) {
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
