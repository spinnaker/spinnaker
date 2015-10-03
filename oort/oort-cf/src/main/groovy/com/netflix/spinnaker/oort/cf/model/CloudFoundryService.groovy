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

import com.netflix.spinnaker.mort.model.SecurityGroup
import com.netflix.spinnaker.mort.model.securitygroups.Rule
import groovy.transform.EqualsAndHashCode
import org.cloudfoundry.client.lib.domain.CloudService

/**
 * Representation for a Cloud Foundry service.
 *
 * @author Greg Turnquist
 */
@EqualsAndHashCode(includes = ["id"])
class CloudFoundryService implements SecurityGroup {

  String type
  String id
  String name
  String application
  String accountName
  String region

  CloudService nativeService

  @Override
  com.netflix.spinnaker.oort.cf.model.CloudFoundryServiceSummary getSummary() {
    new com.netflix.spinnaker.oort.cf.model.CloudFoundryServiceSummary(name: name, id: id)
  }

  @Override
  Set<Rule> getInboundRules() {
    Collections.emptySet()
  }

  @Override
  Set<Rule> getOutboundRules() {
    Collections.emptySet()
  }
}
