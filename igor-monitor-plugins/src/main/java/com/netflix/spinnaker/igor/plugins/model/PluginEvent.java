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
package com.netflix.spinnaker.igor.plugins.model;

import com.google.common.collect.ImmutableMap;
import com.netflix.spinnaker.igor.history.model.Event;
import java.util.Map;
import lombok.Data;
import lombok.EqualsAndHashCode;

@EqualsAndHashCode(callSuper = true)
@Data
public class PluginEvent extends Event {

  private final PluginRelease content;
  private final Map<String, String> details =
      ImmutableMap.<String, String>builder().put("type", "plugin").put("source", "front50").build();

  public PluginEvent(PluginRelease pluginRelease) {
    this.content = pluginRelease;
  }
}
