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
package com.netflix.spinnaker.config;

import com.google.common.base.Strings;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@ConfigurationProperties("cachingAgent.projectClusters")
public class ProjectClustersCachingAgentProperties {

  /**
   * A list of allowed project names that will be cached.
   */
  List<String> allowList = new ArrayList<>();

  public List<String> getAllowList() {
    return allowList;
  }

  public void setAllowList(List<String> allowList) {
    this.allowList = allowList;
  }

  public List<String> getNormalizedAllowList() {
    return allowList.stream()
      .filter(p -> !Strings.isNullOrEmpty(p))
      .map(String::toLowerCase)
      .collect(Collectors.toList());
  }
}
