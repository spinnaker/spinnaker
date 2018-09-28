/*
 * Copyright 2018 Amazon.com, Inc. or its affiliates. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License").
 * You may not use this file except in compliance with the License.
 * A copy of the License is located at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * or in the "license" file accompanying this file. This file is distributed
 * on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 * express or implied. See the License for the specific language governing
 * permissions and limitations under the License.
 */

package com.netflix.spinnaker.clouddriver.ecs.controllers


import com.netflix.spinnaker.clouddriver.ecs.cache.model.Secret
import com.netflix.spinnaker.clouddriver.ecs.provider.view.EcsSecretProvider
import spock.lang.Specification
import spock.lang.Subject

class EcsSecretControllerSpec extends Specification {

  def provider = Mock(EcsSecretProvider)
  @Subject
  def controller = new EcsSecretController(provider)

  def 'should retrieve a collection of secrets'() {
    given:
    def numberOfSecrets = 5
    def givenSecrets = []
    for (int x = 0; x < numberOfSecrets; x++) {
      givenSecrets << new Secret(
        account: 'test-account-' + x,
        region: 'us-west-' + x,
        name: 'secret-name-' + x,
        arn: 'secret-arn-' + x
      )
    }
    provider.allSecrets >> givenSecrets


    when:
    def retrievedSecrets = controller.getAllSecrets()

    then:
    retrievedSecrets == givenSecrets
  }

}
