/*
 * Copyright 2016 Veritas Technologies LLC.
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

package com.netflix.spinnaker.clouddriver.openstack.deploy

import com.netflix.frigga.Names
import com.netflix.spinnaker.clouddriver.helpers.AbstractServerGroupNameResolver
import com.netflix.spinnaker.clouddriver.openstack.security.OpenstackCredentials
import org.openstack4j.model.heat.Stack

import java.text.SimpleDateFormat

class OpenstackServerGroupNameResolver extends AbstractServerGroupNameResolver {
  private static final String PHASE = "DEPLOY"

  private final String region
  private final OpenstackCredentials credentials

  OpenstackServerGroupNameResolver(OpenstackCredentials credentials, String region) {
    this.credentials = credentials
    this.region = region
  }

  @Override
  String getPhase() {
    return PHASE
  }

  @Override
  String getRegion() {
    return region
  }

  @Override
  List<AbstractServerGroupNameResolver.TakenSlot> getTakenSlots(String clusterName) {
    def stacks = credentials.provider.listStacks(region)

    return stacks.findResults { Stack stack ->
      def names = Names.parseName(stack.name)

      if (names.cluster == clusterName) {
        return new AbstractServerGroupNameResolver.TakenSlot(
          serverGroupName: stack.name,
          sequence: names.sequence,
          createdTime: new Date(new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss").parse(stack.creationTime).getTime())
        )
      } else {
        return null
      }
    }
  }
}
