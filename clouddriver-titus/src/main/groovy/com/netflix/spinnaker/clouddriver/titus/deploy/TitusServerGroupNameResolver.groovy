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

package com.netflix.spinnaker.clouddriver.titus.deploy

import com.netflix.frigga.Names
import com.netflix.spinnaker.clouddriver.helpers.AbstractServerGroupNameResolver
import com.netflix.spinnaker.clouddriver.titus.client.TitusClient
import com.netflix.spinnaker.clouddriver.titus.client.model.Job

class TitusServerGroupNameResolver extends AbstractServerGroupNameResolver {

  private static final String TITUS_PHASE = "TITUS_DEPLOY"

  private final TitusClient titusClient
  final String region

  TitusServerGroupNameResolver(TitusClient titusClient, String region) {
    this.titusClient = titusClient
    this.region = region
  }

  @Override
  String getPhase() {
    return TITUS_PHASE
  }

  @Override
  List<AbstractServerGroupNameResolver.TakenSlot> getTakenSlots(String clusterName) {
    def clusterNameParts = Names.parseName(clusterName)
    List<Job> jobs = titusClient.findJobsByApplication(clusterNameParts.app)
                                .findAll { it.name?.startsWith(clusterName) }

    return jobs.collect { Job job ->
      return new AbstractServerGroupNameResolver.TakenSlot(
        serverGroupName: job.name,
        sequence       : Names.parseName(job.name).sequence,
        createdTime    : job.submittedAt
      )
    }
  }
}
