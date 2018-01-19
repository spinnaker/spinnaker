/*
 * Copyright 2017 Lookout, Inc.
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

package com.netflix.spinnaker.clouddriver.ecs.provider.view

import com.netflix.spinnaker.clouddriver.ecs.cache.client.IamRoleCacheClient
import com.netflix.spinnaker.clouddriver.ecs.cache.model.IamRole
import spock.lang.Specification
import spock.lang.Subject

class EcsRoleProviderSpec extends Specification {
  def cacheClient = Mock(IamRoleCacheClient)
  @Subject
  def provider = new EcsRoleProvider(cacheClient)


  def 'should return IAM roles'() {
    given:
    int numberOfRoles = 5
    def givenRoles = []
    for (int x = 0; x < numberOfRoles; x++) {
      givenRoles << new IamRole(
        id: 'role-id-' + x,
        name: 'role-name-' + x,
        accountName: 'account-name-' + x,
        trustRelationships: []
      )
    }
    cacheClient.getAll() >> givenRoles

    when:
    Collection<IamRole> retrievedRoles = provider.getAll()

    then:
    givenRoles == retrievedRoles
  }
}
