/*
 * Copyright 2017 Netflix, Inc.
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

package com.netflix.spinnaker.clouddriver.elasticsearch.ops

import com.netflix.spinnaker.clouddriver.core.services.Front50Service
import com.netflix.spinnaker.clouddriver.data.task.Task
import com.netflix.spinnaker.clouddriver.data.task.TaskRepository
import com.netflix.spinnaker.clouddriver.elasticsearch.descriptions.BulkUpsertEntityTagsDescription
import com.netflix.spinnaker.clouddriver.elasticsearch.descriptions.UpsertEntityTagsDescription
import com.netflix.spinnaker.clouddriver.elasticsearch.model.ElasticSearchEntityTagsProvider
import com.netflix.spinnaker.clouddriver.model.EntityTags
import com.netflix.spinnaker.clouddriver.model.EntityTags.EntityRef
import com.netflix.spinnaker.clouddriver.model.EntityTags.EntityTag
import com.netflix.spinnaker.clouddriver.model.EntityTags.EntityTagMetadata
import com.netflix.spinnaker.clouddriver.model.EntityTags.EntityTagValueType
import com.netflix.spinnaker.clouddriver.security.AccountCredentials
import com.netflix.spinnaker.clouddriver.security.AccountCredentialsProvider
import com.netflix.spinnaker.kork.core.RetrySupport
import spock.lang.Specification
import spock.lang.Unroll

class BulkUpsertEntityTagsAtomicOperationSpec extends Specification {

  def testCredentials = Mock(AccountCredentials) {
    getAccountId() >> { return "100" }
    getName() >> { return "test" }
  }

  def retrySupport = Spy(RetrySupport) {
    _ * sleep(_) >> { /* do nothing */ }
  }
  def front50Service = Mock(Front50Service)
  def accountCredentialsProvider = Mock(AccountCredentialsProvider)
  def entityTagsProvider = Mock(ElasticSearchEntityTagsProvider)

  BulkUpsertEntityTagsDescription description
  BulkUpsertEntityTagsAtomicOperation operation

  def setupSpec() {
    TaskRepository.threadLocalTask.set(Mock(Task))
  }

  def setup() {
    description = new BulkUpsertEntityTagsDescription()
    operation = new BulkUpsertEntityTagsAtomicOperation(
      retrySupport, front50Service, accountCredentialsProvider, entityTagsProvider, description
    )
  }

  void "should perform bulk operation"() {
    given:
    (1..1000).each { addTag(it) }

    when:
    operation.operate([])

    then:
    1000 * accountCredentialsProvider.getAll() >> { return [testCredentials] }
    20 * front50Service.getAllEntityTagsById(_) >> []
    20 * front50Service.batchUpdate(_) >> {
      description.entityTags.findResults { new EntityTags(id: it.id, lastModified: 123, lastModifiedBy: "unknown")}
    }
    20 * entityTagsProvider.bulkIndex(_)
    1000 * entityTagsProvider.verifyIndex(_)
  }

  void 'should set id and pattern to default if none supplied'() {
    given:
    def tag = new UpsertEntityTagsDescription()
    tag.entityRef = new EntityRef(
      cloudProvider: "aws", entityType: "servergroup", entityId: "orca-v001", account: "test", region: "us-east-1"
    )

    when:
    def entityRefId = operation.entityRefId(accountCredentialsProvider, tag)

    then:
    entityRefId.id == "aws:servergroup:orca-v001:100:us-east-1"
    entityRefId.idPattern == "{{cloudProvider}}:{{entityType}}:{{entityId}}:{{account}}:{{region}}"
    1 * accountCredentialsProvider.getAll() >> { return [testCredentials] }
  }

  void 'should merge tags with duplicate entityRefIds'() {
    given:
    (1..4).each {addTag(it)}
    description.entityTags[2].entityRef.entityId = description.entityTags[0].entityRef.entityId
    description.entityTags[3].entityRef.entityId = description.entityTags[0].entityRef.entityId

    when:
    operation.operate([])

    then:
    description.entityTags.size() == 2
    description.entityTags[0].tags.size() == 3
    4 * accountCredentialsProvider.getAll() >> { return [testCredentials] }
    1 * front50Service.getAllEntityTagsById(_) >> []
    1 * front50Service.batchUpdate(_) >> {
      description.entityTags.findResults { new EntityTags(id: it.id, lastModified: 123, lastModifiedBy: "unknown")}
    }
  }

  void 'should create new tag if none exists'() {
    given:
    def tag = new UpsertEntityTagsDescription()
    description.entityTags = [tag]
    tag.entityRef = new EntityRef(
      cloudProvider: "aws", entityType: "servergroup", entityId: "orca-v001", accountId: "100", region: "us-east-1"
    )
    tag.tags = buildTags(["tag1": "some tag"])

    when:
    operation.operate([])

    then:
    tag.id == "aws:servergroup:orca-v001:100:us-east-1"
    tag.idPattern == "{{cloudProvider}}:{{entityType}}:{{entityId}}:{{account}}:{{region}}"
    tag.tagsMetadata*.name == ["tag1"]
    tag.tagsMetadata[0].createdBy == 'unknown'
    tag.tagsMetadata[0].created != null
    tag.tagsMetadata[0].lastModified == tag.tagsMetadata[0].created
    tag.tagsMetadata[0].lastModifiedBy == tag.tagsMetadata[0].createdBy

    1 * accountCredentialsProvider.getAll() >> { return [testCredentials] }
    1 * front50Service.batchUpdate(_) >> {
      [new EntityTags(id: "aws:servergroup:orca-v001:100:us-east-1", lastModified: 123, lastModifiedBy: "unknown")]
    }
    1 * front50Service.getAllEntityTagsById(_) >> []
    1 * entityTagsProvider.bulkIndex(description.entityTags)
    1 * entityTagsProvider.verifyIndex(tag)
  }

  void 'should only set modified/by metadata for partial upsert when tag exists'() {
    given:
    def tag = new UpsertEntityTagsDescription()
    description.entityTags = [tag]
    EntityTags current = new EntityTags(
      tags: buildTags([tag1: "old tag", tag2: "unchanged tag"]),
      tagsMetadata: [
        new EntityTagMetadata(name: "tag1", created: 1L, createdBy: "chris", lastModified: 2L, lastModifiedBy: "adam"),
        new EntityTagMetadata(name: "tag2", created: 1L, createdBy: "chris", lastModified: 2L, lastModifiedBy: "adam")
      ])
    tag.entityRef = new EntityRef(
      cloudProvider: "aws",
      entityType: "servergroup",
      entityId: "orca-v001",
      attributes: [account: "test", region: "us-east-1"]
    )
    tag.tags = buildTags(["tag1": "some tag"])

    def now = new Date()

    when:
    def wasModified = operation.mergeExistingTagsAndMetadata(now, current, tag, true)

    then:
    wasModified == true

    tag.tagsMetadata[0].created == 1L
    tag.tagsMetadata[0].createdBy == "chris"
    tag.tagsMetadata[0].lastModified == now.time
    tag.tagsMetadata[0].lastModifiedBy == "unknown"
    tag.tagsMetadata[1].created == 1L
    tag.tagsMetadata[1].createdBy == "chris"
    tag.tagsMetadata[1].lastModified == 2L
    tag.tagsMetadata[1].lastModifiedBy == "adam"
  }

  void 'should preserve existing tags when merging'() {
    given:
    def tag = new UpsertEntityTagsDescription(
      tags: Collections.singletonList(buildTags(["tag1": "updated tag"])[0])
    )

    description.entityTags = [tag]
    EntityTags current = new EntityTags(
      tags: buildTags([tag1: "old tag", tag2: "unchanged tag"]),
      tagsMetadata: [
        new EntityTagMetadata(name: "tag1", created: 1L, createdBy: "chris", lastModified: 2L, lastModifiedBy: "adam"),
        new EntityTagMetadata(name: "tag2", created: 1L, createdBy: "chris", lastModified: 2L, lastModifiedBy: "adam")
      ])
    tag.entityRef = new EntityRef(
      cloudProvider: "aws",
      entityType: "servergroup",
      entityId: "orca-v001",
      attributes: [account: "test", region: "us-east-1"]
    )

    def now = new Date()

    when:
    def wasModified = operation.mergeExistingTagsAndMetadata(now, current, tag, true)

    then:
    wasModified == true

    tag.tags.size() == 2
    tag.tags.find { it.name == "tag1" }.value == "updated tag"
    tag.tags.find { it.name == "tag2" }.value == "unchanged tag"
  }

  void 'should not halt on exception, but include in results'() {
    given:
    (1..4).each {addTag(it)}
    description.entityTags[2].entityRef.accountId = "101"

    when:
    BulkUpsertEntityTagsAtomicOperationResult result = operation.operate([])

    then:
    result.failures.size() == 1
    result.upserted.size() == 3
    description.entityTags.size() == 3
    4 * accountCredentialsProvider.getAll() >> { return [testCredentials] }
    1 * front50Service.getAllEntityTagsById(_) >> []
    1 * front50Service.batchUpdate(_) >> {
      description.entityTags.findResults { new EntityTags(id: it.id, lastModified: 123, lastModifiedBy: "unknown")}
    }
    entityTagsProvider.index()
  }

  void 'should throw an exception if the account name cannot be found'() {
    given:
    def tag = new UpsertEntityTagsDescription()
    tag.entityRef = new EntityRef(
      cloudProvider: "aws", entityType: "servergroup", entityId: "orca-v001", account: "fake", region: "us-east-1"
    )

    when:
    operation.entityRefId(accountCredentialsProvider, tag)

    then:
    1 * accountCredentialsProvider.getAll() >> { return [testCredentials] }
    thrown IllegalArgumentException
  }

  void 'should throw an exception if the account id cannot be found'() {
    given:
    def tag = new UpsertEntityTagsDescription()
    tag.entityRef = new EntityRef(
      cloudProvider: "aws", entityType: "servergroup", entityId: "orca-v001", accountId: "fake", region: "us-east-1"
    )

    when:
    operation.entityRefId(accountCredentialsProvider, tag)

    then:
    1 * accountCredentialsProvider.getAll() >> { return [testCredentials] }
    thrown IllegalArgumentException
  }

  @Unroll
  void 'should detect whether entity tags have been modified'() {
    given:
    EntityTags currentTags = new EntityTags(
      tags: buildTags(cur)
    )
    EntityTags updatedTags = new EntityTags(
      tags: buildTags(updated)
    )

    expect:
    BulkUpsertEntityTagsAtomicOperation.mergeExistingTagsAndMetadata(
      new Date(), currentTags, updatedTags, isPartial
    ) == expectedToBeModified

    where:
    cur                          | updated          | isPartial || expectedToBeModified
    [foo: "bar"]                 | [foo: "bar"]     | false     || false
    [foo: "bar"]                 | [foo: "bar"]     | true      || false
    [foo: "bar"]                 | [foo: "not bar"] | false     || true
    [foo: "bar"]                 | [foo: "not bar"] | true      || true
    ["not foo": "bar"]           | [foo: "not bar"] | false     || true
    ["not foo": "bar"]           | [foo: "not bar"] | true      || true
    ["not foo": "bar"]           | [:]              | false     || true
    ["not foo": "bar"]           | [:]              | true      || false      // a no-op, should be skipped (partial empty update)
    [:]                          | [foo: "bar"]     | false     || true
    [:]                          | [foo: "bar"]     | true      || true
    ["foo": "bar", "bar": "baz"] | [bar: "baz"]     | false     || true
    ["foo": "bar", "bar": "baz"] | [bar: "baz"]     | true      || false
   }

  private void addTag(Integer index) {
    def tag = new UpsertEntityTagsDescription()
    tag.entityRef = new EntityRef(
      cloudProvider: "aws", entityType: "servergroup", entityId: "orca-v00$index", accountId: "100", region: "us-east-1"
    )
    tag.tags = [new EntityTag(name: "tag-$index", value: "$index", valueType: EntityTagValueType.literal)]
    description.entityTags.add(tag)
  }

  private Collection<EntityTag> buildTags(Map<String, String> tags) {
    return tags.collect { k, v -> new EntityTag(name: k, value: v, valueType: EntityTagValueType.literal) }
  }

}
