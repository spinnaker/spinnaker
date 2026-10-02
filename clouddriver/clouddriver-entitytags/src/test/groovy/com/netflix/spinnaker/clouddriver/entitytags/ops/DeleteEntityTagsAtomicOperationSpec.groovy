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

package com.netflix.spinnaker.clouddriver.entitytags.ops

import com.netflix.spinnaker.clouddriver.data.task.Task
import com.netflix.spinnaker.clouddriver.data.task.TaskRepository
import com.netflix.spinnaker.clouddriver.entitytags.descriptions.DeleteEntityTagsDescription
import com.netflix.spinnaker.clouddriver.model.EntityTags
import com.netflix.spinnaker.clouddriver.model.EntityTagsProvider
import spock.lang.Specification

class DeleteEntityTagsAtomicOperationSpec extends Specification {

  EntityTagsProvider entityTagsProvider
  DeleteEntityTagsDescription description
  DeleteEntityTagsAtomicOperation operation

  def setup() {
    entityTagsProvider = Mock(EntityTagsProvider)
    description = new DeleteEntityTagsDescription()
    operation = new DeleteEntityTagsAtomicOperation(entityTagsProvider, description)
  }

  def setupSpec() {
    TaskRepository.threadLocalTask.set(Mock(Task))
  }

  void 'should delete entityTag from provider if not found'() {
    when:
    description.id = 'abc'
    operation.operate([])

    then:
    1 * entityTagsProvider.get('abc') >> Optional.empty()
    1 * entityTagsProvider.delete('abc')
    0 * _
  }

  void 'should delete entire entityTag if deleteAll flag is set'() {
    given:
    description.id = 'abc'
    description.deleteAll = true
    description.tags = ['a']
    EntityTags current = new EntityTags(tags: buildTags([a: 'something', b: 'something else']))

    when:
    operation.operate([])

    then:
    1 * entityTagsProvider.get('abc') >> Optional.of(current)
    1 * entityTagsProvider.delete('abc')
    0 * _
  }

  void 'should delete entire entityTag if all existing tags requested for deletion'() {
    given:
    description.id = 'abc'
    description.tags = ['a', 'b']
    EntityTags current = new EntityTags(tags: buildTags([a: 'something', b: 'something else']))

    when:
    operation.operate([])

    then:
    1 * entityTagsProvider.get('abc') >> Optional.of(current)
    1 * entityTagsProvider.delete('abc')
    0 * _
  }

  void 'should only delete requested tags and metadata if other tags exist'() {
    given:
    description.id = 'abc'
    description.tags = ['a']
    EntityTags current = new EntityTags(
      id: 'abc',
      tags: buildTags([a: 'something', b: 'something else']),
      tagsMetadata: [
        new EntityTags.EntityTagMetadata(name: "a"),
        new EntityTags.EntityTagMetadata(name: "b")
      ])

    when:
    operation.operate([])

    then:
    current.tags*.name == ['b']
    current.tags*.value == ['something else']
    current.tagsMetadata*.name == ['b']
    1 * entityTagsProvider.get('abc') >> Optional.of(current)
    1 * entityTagsProvider.index(current)
    0 * _
  }

  Collection<EntityTags.EntityTag> buildTags(Map<String, String> tags) {
    return tags.collect { k, v -> new EntityTags.EntityTag(name: k, value: v) }
  }
}
