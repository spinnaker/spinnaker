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
import com.netflix.spectator.api.Registry;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public class Pipeline extends Execution<Pipeline> {
  private String application;

  public @Nonnull String getApplication() {
    return application;
  }

  public void setApplication(@Nonnull String application) {
    this.application = application;
  }

  private String name;

  public @Nullable String getName() {
    return name;
  }

  public void setName(@Nullable String name) {
    this.name = name;
  }

  private String pipelineConfigId;

  public @Nullable String getPipelineConfigId() {
    return pipelineConfigId;
  }

  public void setPipelineConfigId(@Nullable String pipelineConfigId) {
    this.pipelineConfigId = pipelineConfigId;
  }

  private final Map<String, Object> trigger = new HashMap<>();

  public @Nonnull Map<String, Object> getTrigger() {
    return trigger;
  }

  private final List<Map<String, Object>> notifications = new ArrayList<>();

  public @Nonnull List<Map<String, Object>> getNotifications() {
    return notifications;
  }

  private final Map<String, Serializable> initialConfig = new HashMap<>();

  public @Nonnull Map<String, Serializable> getInitialConfig() {
    return initialConfig;
  }

  @Override public final boolean equals(Object o) {
    return super.equals(o);
  }

  @Override public final int hashCode() {
    return super.hashCode();
  }

  public static PipelineBuilder builder(Registry registry) {
    return new PipelineBuilder(registry);
  }

  public static PipelineBuilder builder() {
    return new PipelineBuilder();
  }
}
