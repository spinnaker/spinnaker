/*
 * Copyright 2026 Harness, Inc.
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

package com.netflix.spinnaker.clouddriver.ecs.security

import com.netflix.spinnaker.clouddriver.aws.AmazonCloudProvider
import com.netflix.spinnaker.clouddriver.ecs.TestCredential
import com.netflix.spinnaker.credentials.CompositeCredentialsRepository
import com.netflix.spinnaker.credentials.CredentialsRepository
import com.netflix.spinnaker.credentials.definition.CredentialsDefinitionSource
import com.netflix.spinnaker.credentials.definition.CredentialsParser
import spock.lang.Specification

class EcsCredentialsLoaderSpec extends Specification {

  def account = new ECSCredentialsConfig.Account() {{
    setName("one-ecs")
    setAwsAccount("one")
  }}

  def definitionSource = Mock(CredentialsDefinitionSource) {
    getCredentialsDefinitions() >> [account]
  }
  def parser = Mock(CredentialsParser)
  def repository = Mock(CredentialsRepository) {
    getAll() >> []
  }
  def compositeCredentialsRepository = Mock(CompositeCredentialsRepository)
  def ecsCredentials = Mock(NetflixECSCredentials)

  def loader = new EcsCredentialsLoader(definitionSource, parser, repository, compositeCredentialsRepository)

  def "re-parses an ECS account when its backing AWS account's credentials change, but not otherwise"() {
    given:
    def awsCredsV1 = TestCredential.named("one")
    def awsCredsV2 = TestCredential.named("one") // a distinct instance, e.g. after a new region is added
    compositeCredentialsRepository.getCredentials("one", AmazonCloudProvider.ID) >>> [awsCredsV1, awsCredsV1, awsCredsV2]

    when: "loaded for the first time"
    loader.load()

    then:
    1 * parser.parse(account) >> ecsCredentials
    1 * repository.save(ecsCredentials)

    when: "loaded again while the backing AWS account is unchanged"
    loader.load()

    then: "the ECS account definition hasn't changed, so it isn't re-parsed"
    0 * parser.parse(_)

    when: "loaded again after the backing AWS account changes (new credentials instance)"
    loader.load()

    then: "the ECS account is re-parsed even though its own definition is unchanged"
    1 * parser.parse(account) >> ecsCredentials
  }
}
