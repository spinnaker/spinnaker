/*
 * Copyright 2016 Netflix, Inc.
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
import com.netflix.spinnaker.clouddriver.elasticsearch.descriptions.UpsertEntityTagsDescription
import com.netflix.spinnaker.clouddriver.elasticsearch.model.ElasticSearchEntityTagsProvider
import com.netflix.spinnaker.clouddriver.model.EntityTags
import com.netflix.spinnaker.clouddriver.model.EntityTags.EntityRef
import com.netflix.spinnaker.clouddriver.model.EntityTags.EntityTagMetadata
import com.netflix.spinnaker.clouddriver.model.EntityTagsProvider
import com.netflix.spinnaker.clouddriver.security.AccountCredentials
import com.netflix.spinnaker.clouddriver.security.AccountCredentialsProvider
import spock.lang.Specification

class UpsertEntityTagsAtomicOperationSpec extends Specification {
  def testCredentials = Mock(AccountCredentials) {
    getAccountId() >> { return "100" }
    getName() >> { return "test" }
  }

  def front50Service = Mock(Front50Service)
  def accountCredentialsProvider = Mock(AccountCredentialsProvider)
  def entityTagsProvider = Mock(ElasticSearchEntityTagsProvider)
  def description = new UpsertEntityTagsDescription()
  def operation = new UpsertEntityTagsAtomicOperation(front50Service, accountCredentialsProvider, entityTagsProvider, description)

  def setupSpec() {
    TaskRepository.threadLocalTask.set(Mock(Task))
  }

  void 'should set id and pattern to default if none supplied'() {
    given:
    description.entityRef = new EntityRef(
      cloudProvider: "aws", entityType: "servergroup", entityId: "orca-v001", account: "test", region: "us-east-1"
    )
    description.tags = buildTags(["tag1": "some tag"])

    when:
    def entityRefId = operation.entityRefId(accountCredentialsProvider, description)

    then:
    entityRefId.id == "aws:servergroup:orca-v001:100:us-east-1"
    entityRefId.idPattern == "{{cloudProvider}}:{{entityType}}:{{entityId}}:{{account}}:{{region}}"
    1 * accountCredentialsProvider.getCredentials('test') >> { return testCredentials }
  }

  void 'should create new tag if none exists'() {
    given:
    description.entityRef = new EntityRef(
      cloudProvider: "aws", entityType: "servergroup", entityId: "orca-v001", accountId: "100", region: "us-east-1"
    )
    description.tags = buildTags(["tag1": "some tag"])

    when:
    operation.operate([])

    then:
    description.id == "aws:servergroup:orca-v001:100:us-east-1"
    description.idPattern == "{{cloudProvider}}:{{entityType}}:{{entityId}}:{{account}}:{{region}}"
    description.tagsMetadata*.name == ["tag1"]
    description.tagsMetadata[0].createdBy == 'unknown'
    description.tagsMetadata[0].created != null
    description.tagsMetadata[0].lastModified == description.tagsMetadata[0].created
    description.tagsMetadata[0].lastModifiedBy == description.tagsMetadata[0].createdBy

    1 * accountCredentialsProvider.getAll() >> { return [testCredentials] }
    1 * front50Service.saveEntityTags(description) >> {
      return new EntityTags(lastModified: 123, lastModifiedBy: "unknown")
    }
    1 * entityTagsProvider.index(description)
    1 * entityTagsProvider.verifyIndex(description)
  }


  void 'should only set modified/by metadata for partial upsert when tag exists'() {
    given:
    EntityTags current = new EntityTags(
      tags: buildTags([tag1: "old tag", tag2: "unchanged tag"]),
      tagsMetadata: [
        new EntityTagMetadata(name: "tag1", created: 1L, createdBy: "chris", lastModified: 2L, lastModifiedBy: "adam"),
        new EntityTagMetadata(name: "tag2", created: 1L, createdBy: "chris", lastModified: 2L, lastModifiedBy: "adam")
      ])
    description.entityRef = new EntityRef(
      cloudProvider: "aws",
      entityType: "servergroup",
      entityId: "orca-v001",
      attributes: [account: "test", region: "us-east-1"]
    )
    description.tags = buildTags(["tag1": "some tag"])
    description.isPartial = true

    def now = new Date()

    when:
    operation.mergeExistingTagsAndMetadata(now, current, description)

    then:
    description.tagsMetadata[0].created == 1L
    description.tagsMetadata[0].createdBy == "chris"
    description.tagsMetadata[0].lastModified == now.time
    description.tagsMetadata[0].lastModifiedBy == "unknown"
    description.tagsMetadata[1].created == 1L
    description.tagsMetadata[1].createdBy == "chris"
    description.tagsMetadata[1].lastModified == 2L
    description.tagsMetadata[1].lastModifiedBy == "adam"
  }

  void 'should preserve existing tags when merging'() {
    given:
    EntityTags current = new EntityTags(
      tags: buildTags([tag1: "old tag", tag2: "unchanged tag"]),
      tagsMetadata: [
        new EntityTagMetadata(name: "tag1", created: 1L, createdBy: "chris", lastModified: 2L, lastModifiedBy: "adam"),
        new EntityTagMetadata(name: "tag2", created: 1L, createdBy: "chris", lastModified: 2L, lastModifiedBy: "adam")
      ])
    description.entityRef = new EntityRef(
      cloudProvider: "aws",
      entityType: "servergroup",
      entityId: "orca-v001",
      attributes: [account: "test", region: "us-east-1"]
    )
    description.tags = buildTags(["tag1": "updated tag"])

    def now = new Date()

    when:
    operation.mergeExistingTagsAndMetadata(now, current, description)

    then:
    description.tags.size() == 2
    description.tags.find { it.name == "tag1" }.value == "updated tag"
    description.tags.find { it.name == "tag2" }.value == "unchanged tag"
  }

  Collection<EntityTags.EntityTag> buildTags(Map<String, String> tags) {
    return tags.collect { k, v -> new EntityTags.EntityTag(name: k, value: v, valueType: EntityTags.EntityTagValueType.literal) }
  }
}
