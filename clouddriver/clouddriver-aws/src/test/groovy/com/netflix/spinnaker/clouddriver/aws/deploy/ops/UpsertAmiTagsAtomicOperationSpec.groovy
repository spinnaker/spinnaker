/*
 * Copyright 2017 Netflix, Inc.
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

package com.netflix.spinnaker.clouddriver.aws.deploy.ops

import com.amazonaws.services.ec2.AmazonEC2
import com.amazonaws.services.ec2.model.CreateTagsRequest
import com.amazonaws.services.ec2.model.Tag
import com.netflix.spinnaker.clouddriver.aws.deploy.description.UpsertAmiTagsDescription
import com.netflix.spinnaker.clouddriver.aws.security.AmazonClientProvider
import com.netflix.spinnaker.clouddriver.data.task.Task
import com.netflix.spinnaker.clouddriver.data.task.TaskRepository
import spock.lang.Specification;

class UpsertAmiTagsAtomicOperationSpec extends Specification {
  def task = Mock(Task)
  def amazonEC2 = Mock(AmazonEC2)
  def amazonClientProvider = Mock(AmazonClientProvider)

  def setup() {
    TaskRepository.threadLocalTask.set(task)
  }

  def "should skip update if no tags provided"() {
    given:
    def description = new UpsertAmiTagsDescription()
    def operation = new UpsertAmiTagsAtomicOperation(description)
    operation.amazonClientProvider = amazonClientProvider

    when:
    operation.upsertAmiTags("us-west-1", "my-ami-id", [])

    then:
    1 * task.updateStatus("UPSERT_AMI_TAGS", "Skipping empty tags update for my-ami-id in us-west-1")
    1 * amazonClientProvider.getAmazonEC2(null, "us-west-1") >> { return amazonEC2 }
    0 * amazonEC2.createTags(_)
    0 * _

    when:
    operation.upsertAmiTags("us-west-1", "my-ami-id", [new Tag("my-key", "my-value")])

    then:
    1 * task.updateStatus("UPSERT_AMI_TAGS", "Updating tags for my-ami-id in us-west-1...")
    1 * task.updateStatus("UPSERT_AMI_TAGS", "Tags updated for my-ami-id in us-west-1...")
    1 * amazonClientProvider.getAmazonEC2(null, "us-west-1") >> { return amazonEC2 }
    1 * amazonEC2.createTags({ CreateTagsRequest createTagsRequest ->
      createTagsRequest.tags == [new Tag("my-key", "my-value")]
      createTagsRequest.resources == ["my-ami-id"]
    })
    0 * _
  }
}
