/*
 * Copyright 2014 Netflix, Inc.
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

package com.netflix.bluespar.oort.clusters.aws

import com.netflix.bluespar.oort.clusters.ClusterSummary

class AmazonClusterSummary implements ClusterSummary {
  private final AmazonClusterProvider provider

  private String application
  final String name
  final Integer serverGroupCount
  final Integer instanceCount
  final List<String> serverGroups

  public AmazonClusterSummary(AmazonClusterProvider provider, String application, String name, Integer serverGroupCount, Integer instanceCount, List<String> serverGroups) {
    this.provider = provider
    this.application = application
    this.name = name
    this.serverGroupCount = serverGroupCount
    this.instanceCount = instanceCount
    this.serverGroups = serverGroups
  }

  @Override
  public AmazonCluster getCluster() {
    provider.getByName(application, name)
  }
}
