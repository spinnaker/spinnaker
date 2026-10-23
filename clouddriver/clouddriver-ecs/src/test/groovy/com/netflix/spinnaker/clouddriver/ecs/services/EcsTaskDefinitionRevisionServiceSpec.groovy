/*
 * Copyright 2026 Spinnaker contributors.
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

package com.netflix.spinnaker.clouddriver.ecs.services

import com.netflix.spinnaker.clouddriver.aws.security.AmazonClientProvider
import com.netflix.spinnaker.clouddriver.ecs.security.NetflixECSCredentials
import spock.lang.Specification
import software.amazon.awssdk.services.ecs.EcsClient
import software.amazon.awssdk.services.ecs.model.ListTaskDefinitionsRequest
import software.amazon.awssdk.services.ecs.model.ListTaskDefinitionsResponse

class EcsTaskDefinitionRevisionServiceSpec extends Specification {

  def amazonClientProvider = Mock(AmazonClientProvider)
  def ecs = Mock(EcsClient)
  def service = new EcsTaskDefinitionRevisionService(amazonClientProvider)
  def credentials = Mock(NetflixECSCredentials)

  def 'builds rollback revisions from ARNs without describing every task definition'() {
    given:
    def currentArn = 'arn:aws:ecs:us-west-2:123:task-definition/my-family:7'
    def previousArn = 'arn:aws:ecs:us-west-2:123:task-definition/my-family:6'
    amazonClientProvider.getAmazonEcsV2(credentials, 'us-west-2') >> ecs
    ecs.listTaskDefinitions(_) >> ListTaskDefinitionsResponse.builder()
      .taskDefinitionArns([currentArn, previousArn])
      .build()

    when:
    def revisions = service.listRevisions(credentials, 'us-west-2', currentArn)

    then:
    revisions*.taskDefinitionArn == [currentArn, previousArn]
    revisions*.family == ['my-family', 'my-family']
    revisions*.revision == [7, 6]
    revisions*.current == [true, false]
    revisions*.containerImages == [[], []]
    0 * ecs.describeTaskDefinition(_)
  }

  private static String arn(String family, int revision) {
    "arn:aws:ecs:us-west-2:123:task-definition/${family}:${revision}".toString()
  }

  def 'lists only the exact family, not sibling families that share its prefix'() {
    given:
    amazonClientProvider.getAmazonEcsV2(credentials, 'us-west-2') >> ecs
    // familyPrefix "foo" also matches "foo-bar" and "foobar"; DESC puts the longer names first.
    ecs.listTaskDefinitions(_) >> ListTaskDefinitionsResponse.builder()
      .taskDefinitionArns([arn('foobar', 3), arn('foo-bar', 9), arn('foo-bar', 8), arn('foo', 2), arn('foo', 1)])
      .build()

    when:
    def revisions = service.listRevisions(credentials, 'us-west-2', arn('foo', 2))

    then:
    revisions*.taskDefinitionArn == [arn('foo', 2), arn('foo', 1)]
    revisions*.family == ['foo', 'foo']
    revisions*.current == [true, false]
  }

  def 'keeps paging past a sibling family that fills the first page'() {
    given:
    amazonClientProvider.getAmazonEcsV2(credentials, 'us-west-2') >> ecs

    when:
    def revisions = service.listRevisions(credentials, 'us-west-2', arn('foo', 2))

    then:
    1 * ecs.listTaskDefinitions({ ListTaskDefinitionsRequest r -> r.nextToken() == null && r.familyPrefix() == 'foo' } as ListTaskDefinitionsRequest) >>
      ListTaskDefinitionsResponse.builder()
        .taskDefinitionArns((100..1).collect { arn('foo-bar', it) })
        .nextToken('page-2')
        .build()
    1 * ecs.listTaskDefinitions({ ListTaskDefinitionsRequest r -> r.nextToken() == 'page-2' } as ListTaskDefinitionsRequest) >>
      ListTaskDefinitionsResponse.builder()
        .taskDefinitionArns([arn('foo', 2), arn('foo', 1)])
        .build()
    revisions*.taskDefinitionArn == [arn('foo', 2), arn('foo', 1)]
  }

  def 'stops after a bounded number of pages when siblings never run out'() {
    given:
    amazonClientProvider.getAmazonEcsV2(credentials, 'us-west-2') >> ecs

    when:
    def revisions = service.listRevisions(credentials, 'us-west-2', arn('foo', 2))

    then:
    EcsTaskDefinitionRevisionService.MAX_PAGES * ecs.listTaskDefinitions(_) >>
      ListTaskDefinitionsResponse.builder()
        .taskDefinitionArns([arn('foo-bar', 1)])
        .nextToken('more')
        .build()
    revisions.isEmpty()
  }

  def 'caps listed rollback revisions without loading task-definition bodies'() {
    given:
    amazonClientProvider.getAmazonEcsV2(credentials, 'us-west-2') >> ecs
    ecs.listTaskDefinitions(_) >> ListTaskDefinitionsResponse.builder()
      .taskDefinitionArns((1..60).collect { "arn:aws:ecs:us-west-2:123:task-definition/my-family:${it}".toString() })
      .build()

    when:
    def revisions = service.listRevisions(credentials, 'us-west-2', 'my-family:60')

    then:
    revisions.size() == 10
    revisions.first().revision == 1
    revisions.last().revision == 10
    0 * ecs.describeTaskDefinition(_)
  }
}
