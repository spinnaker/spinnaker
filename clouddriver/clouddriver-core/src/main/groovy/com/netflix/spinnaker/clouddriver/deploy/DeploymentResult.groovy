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

package com.netflix.spinnaker.clouddriver.deploy

import com.netflix.spinnaker.kork.artifacts.model.Artifact

class DeploymentResult {
  List<String> serverGroupNames = []
  Map<String, String> serverGroupNameByRegion = [:]
  List<String> messages = []

  List<String> deployedNames = []
  Map <String, List<String>> deployedNamesByLocation = [:]

  List<Artifact> createdArtifacts = []
  Set<Deployment> deployments = []

  DeploymentResult normalize() {
    if (deployments) {
      return this
    }

    serverGroupNameByRegion.each { key, value ->
      deployments.add(new Deployment(location: key, serverGroupName: value))
    }

    deployedNamesByLocation.each { key, values ->
      values.each { value ->
        deployments.add(new Deployment(location: key, serverGroupName: value))
      }
    }

    return this
  }

  static class Deployment {
    String cloudProvider
    String account
    String location
    String serverGroupName

    Capacity capacity

    Map<String, Object> metadata = [:]

    boolean equals(o) {
      Collections.emptyList()

      if (this.is(o)) return true
      if (getClass() != o.class) return false

      Deployment that = (Deployment) o

      if (location != that.location) return false
      if (serverGroupName != that.serverGroupName) return false

      return true
    }

    int hashCode() {
      int result
      result = (location != null ? location.hashCode() : 0)
      result = 31 * result + (serverGroupName != null ? serverGroupName.hashCode() : 0)
      return result
    }

    @Override
    String toString() {
      return "${location}:${serverGroupName}"
    }

    static class Capacity {
      Integer min
      Integer max
      Integer desired

      boolean equals(o) {
        if (this.is(o)) return true
        if (getClass() != o.class) return false

        Capacity capacity = (Capacity) o

        if (desired != capacity.desired) return false
        if (max != capacity.max) return false
        if (min != capacity.min) return false

        return true
      }

      int hashCode() {
        int result
        result = (min != null ? min.hashCode() : 0)
        result = 31 * result + (max != null ? max.hashCode() : 0)
        result = 31 * result + (desired != null ? desired.hashCode() : 0)
        return result
      }
    }
  }
}
