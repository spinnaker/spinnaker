/*
 * Copyright 2015 The original authors.
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

package com.netflix.spinnaker.oort.cf.model

import com.netflix.spinnaker.oort.model.Application
import groovy.transform.CompileStatic
import groovy.transform.EqualsAndHashCode
import org.cloudfoundry.client.lib.domain.CloudApplication
/**
 * A Cloud Foundry application with all parts (blue/green/complete).
 *
 *
 */
@CompileStatic
@EqualsAndHashCode(includes = ["name"])
class CloudFoundryApplication implements Application, Serializable {

	String name
  Map<String, String> attributes = [:].withDefault {[] as Set<String>}

  CloudApplication nativeApplication

  Map<String, Set<CloudFoundryCluster>> applicationClusters = [:].withDefault {[] as Set<CloudFoundryCluster>}

	@Override
	Map<String, Set<String>> getClusterNames() {
		Map<String, Set<String>> clusterNames = [:].withDefault {[] as Set<String>}

    applicationClusters.each { String k, Set<CloudFoundryCluster> v ->
      clusterNames.get(k).addAll(v.collect {it.name})
    }

    clusterNames
	}

}
