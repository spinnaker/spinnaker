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

package com.netflix.bluespar.oort.clusters

class Clusters {
  private final Map<String, List<ClusterSummary>> clusters

  Clusters() {
    this([:])
  }

  Clusters(Map<String, List<ClusterSummary>> clusters) {
    this.clusters = clusters
  }

  ClusterSummary get(String clusterName) {
    clusters.get(clusterName)
  }

  List<ClusterSummary> list() {
    clusters.values()?.flatten()
  }

  Map getNative() {
    clusters
  }

  void addAll(Map<String, List<ClusterSummary>> objs) {
    for (Map.Entry entry : objs) {
      def app = entry.key
      def clusters = entry.value
      if (!this.clusters.containsKey(app)) {
        this.clusters[app] = []
      }
      this.clusters[app].addAll clusters
    }
  }

  void remove(String name) {
    clusters.remove(name)
  }
}
