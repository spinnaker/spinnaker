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

package com.netflix.spinnaker.clouddriver.elasticsearch

import com.netflix.spinnaker.clouddriver.data.task.DefaultTask
import com.netflix.spinnaker.clouddriver.data.task.Task
import com.netflix.spinnaker.clouddriver.data.task.TaskRepository
import com.netflix.spinnaker.clouddriver.elasticsearch.ops.DeleteEntityTagsAtomicOperation
import spock.lang.Specification
import spock.lang.Unroll;

class ElasticSearchEntityTaggerSpec extends Specification {
  void "should construct valid UpsertEntityTagsDescription"() {
    when:
    def description = ElasticSearchEntityTagger.upsertEntityTagsDescription(
      ElasticSearchEntityTagger.ALERT_TYPE,
      ElasticSearchEntityTagger.ALERT_KEY_PREFIX,
      "myCloudProvider",
      "100",
      "us-east-1",
      "mycategory",
      "servergroup",
      "myServerGroup-v001",
      "MY_EVENT",
      "This server group failed to launch!",
      500L
    )

    then:
    description.isPartial
    description.entityRef.region == "us-east-1"
    description.entityRef.accountId == "100"
    description.entityRef.entityType == "servergroup"
    description.entityRef.entityId == "myServerGroup-v001"
    description.entityRef.cloudProvider == "myCloudProvider"

    description.tags.size() == 1
    description.tags[0].name == "spinnaker_ui_alert:my_event"
    description.tags[0].value == [
      message: "This server group failed to launch!",
      type   : "alert"
    ]
    description.tags[0].timestamp == 500L
    description.tags[0].category == "mycategory"
  }

  @Unroll
  void "should construct valid DeleteEntityTagsDescription"() {
    when:
    def description = ElasticSearchEntityTagger.deleteEntityTagsDescription(
      "myCloudProvider", "100", "us-east-1", "servergroup", "myServerGroup-v001", tags
    )

    then:
    description.deleteAll
    description.id == "mycloudprovider:servergroup:myservergroup-v001:100:us-east-1"
    description.tags == expectedTags

    where:
    tags       || expectedTags
    null       || null
    ["foobar"] || ["foobar"]
  }

  void "should only mutate threadLocalTask if null"() {
    given:
    Task threadLocalTask = null
    def serverGroupTagger = new ElasticSearchEntityTagger(null, null, null) {
      @Override
      protected void run(DeleteEntityTagsAtomicOperation deleteEntityTagsAtomicOperation) {
        threadLocalTask = TaskRepository.threadLocalTask.get()
      }
    }

    when:
    TaskRepository.threadLocalTask.set(null) // cleanup
    serverGroupTagger.deleteAll("aws", "100", "us-east-1", "servergroup", "myServerGroup-v001")

    then:
    threadLocalTask.id == "ElasticSearchEntityTagger"
    TaskRepository.threadLocalTask.get() == null

    when:
    def defaultTask = new DefaultTask("MyDefaultTask")
    TaskRepository.threadLocalTask.set(defaultTask)

    serverGroupTagger.deleteAll("aws", "100", "us-east-1", "servergroup", "myServerGroup-v001")

    then:
    threadLocalTask == defaultTask
    TaskRepository.threadLocalTask.get() == defaultTask
    TaskRepository.threadLocalTask.set(null) // cleanup
  }
}
