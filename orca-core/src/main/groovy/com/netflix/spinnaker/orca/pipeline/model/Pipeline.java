/*
 * Copyright 2014 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
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

package com.netflix.spinnaker.orca.pipeline.model;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.Data;

@Data
public class Pipeline extends Execution<Pipeline> {
  String application;
  String name;
  String pipelineConfigId;
  final Map<String, Object> trigger = new HashMap<>();
  final List<Map<String, Object>> notifications = new ArrayList<>();
  final Map<String, Serializable> initialConfig = new HashMap<>();

  @Override public final boolean equals(Object o) {
    return super.equals(o);
  }

  @Override public final int hashCode() {
    return super.hashCode();
  }

  public static PipelineBuilder builder() {
    return new PipelineBuilder();
  }
}
