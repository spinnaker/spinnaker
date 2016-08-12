/*
 * Copyright 2015 Netflix, Inc.
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


package com.netflix.spinnaker.front50.model.project

import com.fasterxml.jackson.annotation.JsonIgnore
import com.netflix.spinnaker.front50.model.Timestamped

class Project implements Timestamped {
  String id
  String name
  String email

  ProjectConfig config = new ProjectConfig()

  Long updateTs
  Long createTs

  String lastModifiedBy

  @Override
  @JsonIgnore
  Long getLastModified() {
    return updateTs
  }

  @Override
  void setLastModified(Long lastModified) {
    this.updateTs = lastModified
  }

  static class ProjectConfig {
    Collection<PipelineConfig> pipelineConfigs
    Collection<String> applications
    Collection<ClusterConfig> clusters
  }

  static class ClusterConfig {
    String account
    String stack
    String detail
    Collection<String> applications
  }

  static class PipelineConfig {
    String application
    String pipelineConfigId
  }
}
