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
import com.netflix.spinnaker.clouddriver.model.EntityTags.EntityTagValueType
import com.netflix.spinnaker.clouddriver.security.AccountCredentials
import com.netflix.spinnaker.clouddriver.security.AccountCredentialsProvider
import spock.lang.Specification

class BulkUpsertEntityTagsAtomicOperationSpec extends Specification {

  def testCredentials = Mock(AccountCredentials) {
    getAccountId() >> { return "100" }
    getName() >> { return "test" }
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
    operation = new BulkUpsertEntityTagsAtomicOperation(front50Service, accountCredentialsProvider, entityTagsProvider, description)
  }

  void "should perform bulk operation"() {
    given:
    (1..11).each { addTag(it) }

    when:
    operation.operate([])

    then:
    11 * accountCredentialsProvider.getAll() >> { return [testCredentials] }
    11 * front50Service.saveEntityTags(_) >> {
      return new EntityTags(lastModified: 123, lastModifiedBy: "unknown")
    }
    11 * entityTagsProvider.index(_)
    11 * entityTagsProvider.verifyIndex(_)
  }

  private void addTag(Integer index) {
    def tag = new UpsertEntityTagsDescription()
    tag.entityRef = new EntityRef(
      cloudProvider: "aws", entityType: "servergroup", entityId: "orca-v001", accountId: "100", region: "us-east-1"
    )
    tag.tags = [new EntityTag(name: "tag-$index", value: "$index", valueType: EntityTagValueType.literal)]
    description.entityTags.add(tag)
  }

}
