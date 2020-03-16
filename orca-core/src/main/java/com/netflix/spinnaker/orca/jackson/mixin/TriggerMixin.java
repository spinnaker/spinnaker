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
package com.netflix.spinnaker.orca.jackson.mixin;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.netflix.spinnaker.orca.pipeline.model.support.TriggerDeserializer;
import java.util.Map;

@JsonDeserialize(using = TriggerDeserializer.class)
public interface TriggerMixin {

  @JsonProperty("rebake")
  boolean isRebake();

  @JsonProperty("dryRun")
  boolean isDryRun();

  @JsonProperty("strategy")
  boolean isStrategy();

  @JsonAnyGetter
  Map<String, Object> getOther();

  @JsonAnySetter
  void setOther(String key, Object value);
}
