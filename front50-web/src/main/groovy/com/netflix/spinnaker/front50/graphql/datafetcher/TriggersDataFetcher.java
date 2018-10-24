/*
 * Copyright 2018 Netflix, Inc.
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
package com.netflix.spinnaker.front50.graphql.datafetcher;

import com.netflix.spinnaker.front50.model.pipeline.Pipeline;
import com.netflix.spinnaker.front50.model.pipeline.Trigger;
import graphql.schema.DataFetcher;
import graphql.schema.DataFetchingEnvironment;

import java.util.Collection;
import java.util.stream.Collectors;

public class TriggersDataFetcher implements DataFetcher<Collection<Trigger>> {
  @Override
  public Collection<Trigger> get(DataFetchingEnvironment environment) {
    Pipeline pipeline = environment.getSource();
    Collection<Trigger> triggers = pipeline.getTriggers();

    if (environment.containsArgument("types")) {
      Collection<String> types = environment.getArgument("types");
      return triggers.stream()
        .filter(t -> types.contains(t.getType()))
        .collect(Collectors.toList());
    }

    return triggers;
  }
}
