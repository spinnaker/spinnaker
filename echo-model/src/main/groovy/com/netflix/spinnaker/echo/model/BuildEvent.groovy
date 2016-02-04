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


package com.netflix.spinnaker.echo.model

import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import groovy.transform.Canonical

@Canonical
@JsonIgnoreProperties(ignoreUnknown = true)
class BuildEvent {
  Content content;
  Details details;

  public static final String BUILD_EVENT_TYPE = "build";
  public static final String GIT_EVENT_TYPE = "git";

  @Canonical
  @JsonIgnoreProperties(ignoreUnknown = true)
  public static class Content {
    Project project;
    String master;
    String repoProject;
    String slug;
    String hash;
  }

  @Canonical
  @JsonIgnoreProperties(ignoreUnknown = true)
  public static class Details {
    String type;
    String source;
  }

  @Canonical
  @JsonIgnoreProperties(ignoreUnknown = true)
  public static class Project {
    String name;
    Build lastBuild;
  }

  @Canonical
  @JsonIgnoreProperties(ignoreUnknown = true)
  public static class Build {
    boolean building;
    int number;
    Result result;
  }

  public enum Result {
    SUCCESS, UNSTABLE, BUILDING, ABORTED, FAILURE, NOT_BUILT
  }

  @JsonIgnore
  public boolean isBuild() {
    return details.getType() == BUILD_EVENT_TYPE;
  }

  @JsonIgnore
  public boolean isGit() {
    return details.getType() == GIT_EVENT_TYPE;
  }

  @JsonIgnore
  public int getBuildNumber() {
    return isBuild() ? content.getProject().getLastBuild().getNumber() : 0;
  }

  @JsonIgnore
  public String getHash() {
    return isGit() ? content.hash : null;
  }
}
