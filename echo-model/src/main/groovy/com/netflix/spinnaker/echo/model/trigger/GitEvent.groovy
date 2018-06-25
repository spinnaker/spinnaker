/*
 * Copyright 2016 Netflix, Inc.
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

package com.netflix.spinnaker.echo.model.trigger

import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.netflix.spinnaker.kork.artifacts.model.Artifact
import groovy.transform.Canonical

@Canonical
@JsonIgnoreProperties(ignoreUnknown = true)
class GitEvent extends TriggerEvent {
  Content content

  public static final String TYPE = 'GIT'

  @Canonical
  @JsonIgnoreProperties(ignoreUnknown = true)
  static class Content {
    String repoProject
    String slug
    String hash
    String branch
    List<Artifact> artifacts
  }

  @JsonIgnore
  String getHash() {
    return content.hash
  }

  @JsonIgnore
  String getBranch() {
    return content.branch
  }
}

