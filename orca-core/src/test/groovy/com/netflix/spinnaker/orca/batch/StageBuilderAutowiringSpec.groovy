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
