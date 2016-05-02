package com.netflix.spinnaker.orca.kato.pipeline

import com.netflix.spinnaker.orca.DefaultTaskResult
import com.netflix.spinnaker.orca.RetryableTask
import com.netflix.spinnaker.orca.Task
import com.netflix.spinnaker.orca.clouddriver.tasks.MonitorKatoTask
import com.netflix.spinnaker.orca.clouddriver.tasks.servergroup.ServerGroupCacheForceRefreshTask
import com.netflix.spinnaker.orca.clouddriver.tasks.instance.TerminateInstancesTask
import com.netflix.spinnaker.orca.clouddriver.tasks.instance.WaitForDownInstanceHealthTask
import com.netflix.spinnaker.orca.clouddriver.tasks.instance.WaitForTerminatedInstancesTask
import com.netflix.spinnaker.orca.clouddriver.tasks.instance.WaitForUpInstanceHealthTask
import com.netflix.spinnaker.orca.config.JesqueConfiguration
import com.netflix.spinnaker.orca.config.OrcaConfiguration
import com.netflix.spinnaker.orca.config.OrcaPersistenceConfiguration
import com.netflix.spinnaker.orca.jackson.OrcaObjectMapper
import com.netflix.spinnaker.orca.kato.tasks.DisableInstancesTask
import com.netflix.spinnaker.orca.kato.tasks.rollingpush.CheckForRemainingTerminationsTask
import com.netflix.spinnaker.orca.kato.tasks.rollingpush.DetermineTerminationCandidatesTask
import com.netflix.spinnaker.orca.kato.tasks.rollingpush.DetermineTerminationPhaseInstancesTask
import com.netflix.spinnaker.orca.kato.tasks.rollingpush.WaitForNewInstanceLaunchTask
import com.netflix.spinnaker.orca.pipeline.LinearStage
import com.netflix.spinnaker.orca.pipeline.PipelineStarter
import com.netflix.spinnaker.orca.pipeline.model.Stage
import com.netflix.spinnaker.orca.test.JobCompletionListener
import com.netflix.spinnaker.orca.test.TestConfiguration
import com.netflix.spinnaker.orca.test.batch.BatchTestConfiguration
import com.netflix.spinnaker.orca.test.redis.EmbeddedRedisConfiguration
import groovy.transform.CompileStatic
import org.springframework.batch.core.Step
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.AnnotationConfigApplicationContext
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

import static com.netflix.spinnaker.orca.ExecutionStatus.REDIRECT
import static com.netflix.spinnaker.orca.ExecutionStatus.SUCCEEDED

class RollingPushStageSpec extends Specification {

  private static final DefaultTaskResult SUCCESS = new DefaultTaskResult(SUCCEEDED)
  private static final DefaultTaskResult REDIR = new DefaultTaskResult(REDIRECT)

  @AutoCleanup("destroy")
  def applicationContext = new AnnotationConfigApplicationContext()
  @Shared def mapper = new OrcaObjectMapper()
  @Autowired PipelineStarter pipelineStarter

  def endTask = Mock(Task)
  def endStage = new AutowiredTestStage("end", endTask)

  def preCycleTask = Stub(DetermineTerminationCandidatesTask) {
    execute(_) >> SUCCESS
  }
  def startOfCycleTask = Mock(DetermineTerminationPhaseInstancesTask)
  def endOfCycleTask = Stub(CheckForRemainingTerminationsTask)
  def cycleTasks = [DisableInstancesTask, MonitorKatoTask, WaitForDownInstanceHealthTask, TerminateInstancesTask, WaitForTerminatedInstancesTask, ServerGroupCacheForceRefreshTask, WaitForNewInstanceLaunchTask, WaitForUpInstanceHealthTask].collect {
    if (RetryableTask.isAssignableFrom(it)) {
      Stub(it) {
        execute(_) >> SUCCESS
        getBackoffPeriod() >> 5000L
        getTimeout() >> 3600000L
      }
    } else {
      Stub(it) {
        execute(_) >> SUCCESS
      }
    }
  }

  def setup() {
    applicationContext.with {
      register(EmbeddedRedisConfiguration, JesqueConfiguration,
               BatchTestConfiguration, OrcaConfiguration, OrcaPersistenceConfiguration,
               JobCompletionListener, TestConfiguration)
      register(RollingPushStage)
      beanFactory.registerSingleton("endStage", endStage)
      ([preCycleTask, startOfCycleTask, endOfCycleTask] + cycleTasks).each { task ->
        beanFactory.registerSingleton(task.getClass().simpleName, task)
      }
      refresh()

      beanFactory.autowireBean(endStage)
      beanFactory.autowireBean(this)
    }
    endStage.applicationContext = applicationContext
  }

  def "rolling push loops until completion"() {
    given:
    endOfCycleTask.execute(_) >> REDIR >> REDIR >> SUCCESS

    when:
    pipelineStarter.start(configJson)

    then:
    3 * startOfCycleTask.execute(_) >> SUCCESS

    then:
    1 * endTask.execute(_) >> SUCCESS

    where:
    config = [
      application: "app",
      name       : "my-pipeline",
      stages     : [[type: RollingPushStage.PIPELINE_CONFIG_TYPE], [type: "end"]],
      version: 2
    ]
    configJson = mapper.writeValueAsString(config)

  }

  @CompileStatic
  static class AutowiredTestStage extends LinearStage {

    private final List<Task> tasks = []

    AutowiredTestStage(String name, Task... tasks) {
      super(name)
      this.tasks.addAll tasks
    }

    @Override
    public List<Step> buildSteps(Stage stage) {
      def i = 1
      tasks.collect { Task task ->
        buildStep(stage, "task${i++}", task)
      }
    }
  }
}
