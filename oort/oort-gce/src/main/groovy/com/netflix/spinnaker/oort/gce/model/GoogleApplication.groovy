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

package com.netflix.spinnaker.oort.gce.model

import com.netflix.spinnaker.oort.model.Application
import groovy.transform.CompileStatic
import groovy.transform.EqualsAndHashCode

@CompileStatic
@EqualsAndHashCode(includes = ["name"])
class GoogleApplication implements Application, Serializable {
  String name
  Map<String, Set<String>> clusterNames = Collections.synchronizedMap(new HashMap<String, Set<String>>())
  Map<String, String> attributes = Collections.synchronizedMap(new HashMap<String, String>())
  Map<String, Map<String, GoogleCluster>> clusters = Collections.synchronizedMap(new HashMap<String, Map<String, GoogleCluster>>())

  // Used as a deep copy-constructor.
  public static GoogleApplication newInstance(GoogleApplication originalGoogleApplication) {
    GoogleApplication copyGoogleApplication = new GoogleApplication(name: originalGoogleApplication.name)

    originalGoogleApplication.clusterNames.each { accountNameKey, originalClusterNames ->
      copyGoogleApplication.clusterNames[accountNameKey] = new HashSet<String>()
      copyGoogleApplication.clusterNames[accountNameKey].addAll(originalClusterNames)
    }

    originalGoogleApplication.attributes.each {
      copyGoogleApplication.attributes[it.key] = it.value
    }

    originalGoogleApplication.clusters.each { accountNameKey, originalClustersMap ->
      copyGoogleApplication.clusters[accountNameKey] = new HashMap<String, GoogleCluster>()

      originalClustersMap.each { clusterNameKey, originalCluster ->
        copyGoogleApplication.clusters[accountNameKey][clusterNameKey] = GoogleCluster.newInstance(originalCluster)
      }
    }

    copyGoogleApplication
  }
}
