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
 *
 */
package com.netflix.spinnaker.orca;

import static java.lang.String.format;

import com.netflix.spinnaker.orca.api.pipeline.graph.StageDefinitionBuilder;
import java.util.Collection;
import javax.annotation.Nonnull;

/**
 * {@code StageResolver} allows for {@code StageDefinitionBuilder} retrieval via bean name or alias.
 */
public interface StageResolver {

  /**
   * Fetch a {@code StageDefinitionBuilder} by {@code type}.
   *
   * @param type StageDefinitionBuilder type
   * @return the StageDefinitionBuilder matching {@code type} or {@code typeAlias}
   * @throws DefaultStageResolver.NoSuchStageDefinitionBuilderException if StageDefinitionBuilder
   *     does not exist
   */
  @Nonnull
  default StageDefinitionBuilder getStageDefinitionBuilder(@Nonnull String type) {
    return getStageDefinitionBuilder(type, null);
  }

  /**
   * Fetch a {@code StageDefinitionBuilder} by {@code type} or {@code typeAlias}.
   *
   * @param type StageDefinitionBuilder type
   * @param typeAlias StageDefinitionBuilder alias (optional)
   * @return the StageDefinitionBuilder matching {@code type} or {@code typeAlias}
   * @throws DefaultStageResolver.NoSuchStageDefinitionBuilderException if StageDefinitionBuilder
   *     does not exist
   */
  @Nonnull
  StageDefinitionBuilder getStageDefinitionBuilder(@Nonnull String type, String typeAlias);

  class DuplicateStageAliasException extends IllegalStateException {
    DuplicateStageAliasException(String message) {
      super(message);
    }
  }

  class NoSuchStageDefinitionBuilderException extends IllegalArgumentException {
    NoSuchStageDefinitionBuilderException(String type, Collection<String> knownTypes) {
      super(
          format(
              "No StageDefinitionBuilder implementation for %s found (knownTypes: %s)",
              type, String.join(",", knownTypes)));
    }
  }
}
