/*
 * Copyright 2016 Google, Inc.
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

package com.netflix.spinnaker.clouddriver.kubernetes.deploy

import com.netflix.frigga.Names
import com.netflix.spinnaker.clouddriver.helpers.AbstractServerGroupNameResolver
import com.netflix.spinnaker.clouddriver.kubernetes.model.KubernetesModelUtil
import com.netflix.spinnaker.clouddriver.kubernetes.security.KubernetesCredentials
import io.fabric8.kubernetes.api.model.ReplicationController
import io.fabric8.kubernetes.api.model.extensions.Job

class KubernetesJobNameResolver extends AbstractServerGroupNameResolver {

  private static final String PHASE = "START_JOB"

  private final String namespace
  private final KubernetesCredentials credentials

  KubernetesJobNameResolver(String namespace, KubernetesCredentials credentials) {
    this.namespace = namespace
    this.credentials = credentials
  }

  @Override
  String getPhase() {
    return PHASE
  }

  @Override
  String getRegion() {
    return namespace
  }

  @Override
  List<AbstractServerGroupNameResolver.TakenSlot> getTakenSlots(String clusterName) {
    def replicationControllers = credentials.apiAdaptor.getJobs(namespace)

    return replicationControllers.findResults { Job job ->
      def names = Names.parseName(job.metadata.name)

      if (names.cluster == clusterName) {
        return new AbstractServerGroupNameResolver.TakenSlot(
          serverGroupName: job.metadata.name,
          sequence       : names.sequence,
          createdTime    : new Date(KubernetesModelUtil.translateTime(job.metadata.creationTimestamp))
        )
      } else {
        return null
      }
    }
  }
}
