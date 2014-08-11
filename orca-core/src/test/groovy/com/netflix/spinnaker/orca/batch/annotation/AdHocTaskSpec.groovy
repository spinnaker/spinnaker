/*
 * Copyright 2014 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
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

package com.netflix.spinnaker.orca.batch.annotation

import groovy.transform.CompileStatic
import com.netflix.spinnaker.orca.DefaultTaskResult
import com.netflix.spinnaker.orca.Task
import com.netflix.spinnaker.orca.TaskContext
import com.netflix.spinnaker.orca.TaskResult
import com.netflix.spinnaker.orca.annotation.AdHocTask
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.ApplicationContext
import org.springframework.test.context.ContextConfiguration
import spock.lang.Specification
import static com.netflix.spinnaker.orca.TaskResult.Status.SUCCEEDED
import static org.hamcrest.Matchers.not
import static org.hamcrest.Matchers.sameInstance
import static spock.util.matcher.HamcrestSupport.that

@ContextConfiguration(classes = [TestTask])
class AdHocTaskSpec extends Specification {

  @Autowired ApplicationContext applicationContext

  def "ad-hoc tasks are prototype scoped"() {
    expect:
    that applicationContext.getBean(TestTask), not(sameInstance(applicationContext.getBean(TestTask)))
  }
}

@CompileStatic
@AdHocTask
class TestTask implements Task {
  @Override
  TaskResult execute(TaskContext context) {
    new DefaultTaskResult(SUCCEEDED)
  }
}
