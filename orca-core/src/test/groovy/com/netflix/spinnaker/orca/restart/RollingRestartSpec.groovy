package com.netflix.spinnaker.orca.restart

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.orca.DefaultTaskResult
import com.netflix.spinnaker.orca.Task
import com.netflix.spinnaker.orca.batch.StageBuilder
import com.netflix.spinnaker.orca.config.JesqueConfiguration
import com.netflix.spinnaker.orca.config.OrcaConfiguration
import com.netflix.spinnaker.orca.config.OrcaPersistenceConfiguration
import com.netflix.spinnaker.orca.pipeline.PipelineStarter
import com.netflix.spinnaker.orca.pipeline.model.DefaultTask
import com.netflix.spinnaker.orca.pipeline.model.Stage
import com.netflix.spinnaker.orca.pipeline.persistence.ExecutionRepository
import com.netflix.spinnaker.orca.test.JobCompletionListener
import com.netflix.spinnaker.orca.test.TestConfiguration
import com.netflix.spinnaker.orca.test.batch.BatchTestConfiguration
import com.netflix.spinnaker.orca.test.redis.EmbeddedRedisConfiguration
import groovy.transform.CompileDynamic
import groovy.transform.CompileStatic
import org.springframework.batch.core.ExitStatus
import org.springframework.batch.core.StepExecution
import org.springframework.batch.core.configuration.JobRegistry
import org.springframework.batch.core.explore.JobExplorer
import org.springframework.batch.core.job.builder.FlowBuilder
import org.springframework.batch.core.listener.StepExecutionListenerSupport
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.AnnotationConfigApplicationContext
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor
import spock.lang.AutoCleanup
import spock.lang.Specification
import static com.netflix.spinnaker.orca.ExecutionStatus.*

class RollingRestartSpec extends Specification {

  @AutoCleanup("destroy")
  def applicationContext = new AnnotationConfigApplicationContext()
  @Autowired ThreadPoolTaskExecutor taskExecutor
  @Autowired PipelineStarter pipelineStarter
  @Autowired ObjectMapper mapper
  @Autowired JobRegistry jobRegistry
  @Autowired JobExplorer jobExplorer
  @Autowired ExecutionRepository repository
  @Autowired JobCompletionListener jobCompletionListener

  def task1 = Mock(Task)
  def task2 = Mock(Task)

  def setup() {
    def testStage = new RedirectingTestStage("test", task1, task2)
    applicationContext.with {
      register(EmbeddedRedisConfiguration, JesqueConfiguration,
               BatchTestConfiguration, OrcaConfiguration, OrcaPersistenceConfiguration,
               JobCompletionListener, TestConfiguration)
      beanFactory.registerSingleton("testStage", testStage)
      refresh()

      beanFactory.autowireBean(testStage)
      beanFactory.autowireBean(this)
    }
    testStage.applicationContext = applicationContext
  }

  def "a previously run rolling push pipeline can be restarted and redirects work"() {
    given:
    def pipeline = pipelineStarter.create(mapper.readValue(pipelineConfigFor("test"), Map))
    pipeline.stages[0].tasks << new DefaultTask(id: 2, name: "task1", status: REDIRECT,
                                                startTime: System.currentTimeMillis(),
                                                endTime: System.currentTimeMillis())
    pipeline.stages[0].tasks << new DefaultTask(id: 3, name: "task2", status: NOT_STARTED,
                                                startTime: System.currentTimeMillis())
    repository.store(pipeline)

    when:
    pipelineStarter.resume(pipeline)
    jobCompletionListener.await()

    then:
    repository.retrievePipeline(pipeline.id).status.toString() == SUCCEEDED.name()

    then:
    2 * task1.execute(_) >> new DefaultTaskResult(REDIRECT) >> new DefaultTaskResult(SUCCEEDED)
    1 * task2.execute(_) >> new DefaultTaskResult(SUCCEEDED)
  }

  private String pipelineConfigFor(String... stages) {
    def config = [
      application: "app",
      name       : "my-pipeline",
      stages     : stages.collect { [type: it] }
    ]
    mapper.writeValueAsString(config)
  }

  @CompileStatic
  class RedirectingTestStage extends StageBuilder {
    private final Task startTask
    private final Task endTask

    RedirectingTestStage(String name, Task startTask, Task endTask) {
      super(name)
      this.startTask = startTask
      this.endTask = endTask
    }

    @Override
    protected FlowBuilder buildInternal(FlowBuilder jobBuilder, Stage stage) {
      def resetListener = new StepExecutionListenerSupport() {
        @Override @CompileDynamic
        ExitStatus afterStep(StepExecution stepExecution) {
          if (stepExecution.exitStatus.exitCode == REDIRECT.name()) {
            stage.tasks[0].with {
              status = NOT_STARTED
//              startTime = null
              endTime = null
            }
            repository.storeStage(stage)
          }
          stepExecution.exitStatus
        }
      }
      def start = buildStep(stage, "redirecting", startTask, resetListener)
      def end = buildStep(stage, "final", endTask)
      jobBuilder.next(start)
      jobBuilder.on(REDIRECT.name()).to(start)
      jobBuilder.from(start).on("**").to(end)
    }
  }
}
