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

package com.netflix.spinnaker.orca.batch

import com.netflix.spinnaker.orca.Task
import com.netflix.spinnaker.orca.pipeline.SimpleStage
import org.springframework.batch.core.StepExecutionListener
import org.springframework.batch.core.listener.StepExecutionListenerSupport
import org.springframework.context.support.StaticApplicationContext
import spock.lang.Specification
import spock.lang.Unroll
import static org.springframework.beans.factory.support.AbstractBeanDefinition.AUTOWIRE_BY_TYPE

@Unroll
class StageBuilderAutowiringSpec extends Specification {

  def "any #listenerType.simpleName beans registered in the spring context are automatically wired in"() {
    given:
    def applicationContext = new StaticApplicationContext()
    applicationContext.beanFactory.with {
      registerSingleton("listener1", new StepExecutionListenerSupport())
      registerSingleton("listener2", new StepExecutionListenerSupport())
    }

    and:
    def stageBuilder = new SimpleStage("foo", Stub(Task))

    when:
    applicationContext.beanFactory.autowireBeanProperties(stageBuilder, AUTOWIRE_BY_TYPE, false)

    then:
    stageBuilder.taskListeners.size() == applicationContext.getBeansOfType(StepExecutionListener).size()

    where:
    listenerType = StepExecutionListener
  }
}
