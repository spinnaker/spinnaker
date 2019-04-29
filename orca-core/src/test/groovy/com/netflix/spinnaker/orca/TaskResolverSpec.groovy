/*
 * Copyright 2019 Netflix, Inc.
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

package com.netflix.spinnaker.orca

import com.netflix.spinnaker.orca.pipeline.model.Stage
import com.netflix.spinnaker.orca.pipeline.tasks.WaitTask
import spock.lang.Specification
import spock.lang.Subject
import spock.lang.Unroll

import javax.annotation.Nonnull;

class TaskResolverSpec extends Specification {
  @Subject
  def taskResolver = new TaskResolver([
      new WaitTask(),
      new AliasedTask()
  ], false)

  @Unroll
  def "should lookup task by name or alias"() {
    expect:
    taskResolver.getTaskClass(taskTypeIdentifier) == expectedTaskClass

    where:
    taskTypeIdentifier                                        || expectedTaskClass
    "com.netflix.spinnaker.orca.pipeline.tasks.WaitTask"      || WaitTask.class
    "com.netflix.spinnaker.orca.TaskResolverSpec.AliasedTask" || AliasedTask.class
    "com.netflix.spinnaker.orca.NotAliasedTask"               || AliasedTask.class
  }

  def "should raise exception on duplicate alias"() {
    when:
    new TaskResolver([
        new AliasedTask(),
        new AliasedTask()
    ], false)

    then:
    thrown(TaskResolver.DuplicateTaskAliasException)
  }

  def "should raise exception when task not found"() {
    when:
    taskResolver.getTaskClass("DoesNotExist")

    then:
    thrown(TaskResolver.NoSuchTaskException)
  }

  @Task.Aliases("com.netflix.spinnaker.orca.NotAliasedTask")
  class AliasedTask implements Task {
    @Override
    TaskResult execute(@Nonnull Stage stage) {
      return TaskResult.SUCCEEDED
    }
  }
}
