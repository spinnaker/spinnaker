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

package com.netflix.spinnaker.clouddriver.aws.deploy

import com.netflix.spinnaker.clouddriver.helpers.AbstractServerGroupNameResolver
import com.netflix.spinnaker.clouddriver.aws.services.AsgService
import groovy.transform.CompileStatic

@CompileStatic
class AWSServerGroupNameResolver extends AbstractServerGroupNameResolver {

  private static final String AWS_PHASE = "AWS_DEPLOY"

  private final String region
  private final AsgService asgService

  AWSServerGroupNameResolver(String region, AsgService asgService) {
    this.region = region
    this.asgService = asgService
  }

  @Override
  String getPhase() {
    return AWS_PHASE
  }

  @Override
  String getRegion() {
    return region
  }

  @Override
  List<AbstractServerGroupNameResolver.TakenSlot> getTakenSlots(String clusterName) {
    return asgService.getTakenSlots(clusterName)
  }
}
