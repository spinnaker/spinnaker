package com.netflix.spinnaker.orca.batch.persistence

import groovy.transform.CompileStatic
import java.util.concurrent.CountDownLatch
import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.orca.DefaultTaskResult
import com.netflix.spinnaker.orca.Task
import com.netflix.spinnaker.orca.batch.pipeline.TestStage
import com.netflix.spinnaker.orca.config.JesqueConfiguration
import com.netflix.spinnaker.orca.config.OrcaConfiguration
import com.netflix.spinnaker.orca.pipeline.PipelineJobBuilder
import com.netflix.spinnaker.orca.pipeline.PipelineStarter
import com.netflix.spinnaker.orca.pipeline.model.Pipeline
import com.netflix.spinnaker.orca.pipeline.persistence.ExecutionRepository
import com.netflix.spinnaker.orca.test.batch.BatchTestConfiguration
import com.netflix.spinnaker.orca.test.redis.EmbeddedRedisConfiguration
import org.springframework.batch.core.configuration.JobRegistry
import org.springframework.batch.core.configuration.annotation.BatchConfigurer
import org.springframework.batch.core.configuration.annotation.DefaultBatchConfigurer
import org.springframework.batch.core.configuration.annotation.StepBuilderFactory
import org.springframework.batch.core.launch.JobLauncher
import org.springframework.batch.core.launch.support.SimpleJobLauncher
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.support.AbstractApplicationContext
import org.springframework.core.task.TaskExecutor
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor
import org.springframework.test.annotation.DirtiesContext
import org.springframework.test.context.ContextConfiguration
import spock.lang.Specification
import static com.netflix.spinnaker.orca.ExecutionStatus.RUNNING
import static com.netflix.spinnaker.orca.ExecutionStatus.SUCCEEDED
import static org.springframework.test.annotation.DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD

@ContextConfiguration(classes = [
  EmbeddedRedisConfiguration,
  JesqueConfiguration,
  AsyncJobLauncherConfiguration,
  BatchTestConfiguration,
  OrcaConfiguration
])
@DirtiesContext(classMode = AFTER_EACH_TEST_METHOD)
class PipelinePersistenceSpec extends Specification {

  @Autowired ThreadPoolTaskExecutor taskExecutor
  @Autowired PipelineJobBuilder pipelineJobBuilder
  @Autowired PipelineStarter pipelineStarter
  @Autowired AbstractApplicationContext applicationContext
  @Autowired StepBuilderFactory steps
  @Autowired ExecutionRepository executionRepository
  @Autowired ObjectMapper mapper
  @Autowired JobRegistry jobRegistry

  def task1 = Mock(Task)
  def task2 = Mock(Task)

  def setup() {
    def testStage = new TestStage("test", steps, executionRepository, task1, task2)
    applicationContext.beanFactory.registerSingleton("testStage", testStage)

    pipelineJobBuilder.initialize()
  }

  def "if a pipeline dies we can reconstitute it"() {
    given:
    task1.execute(_) >> new DefaultTaskResult(RUNNING)

    and:
    def pipeline = pipelineStarter.start(pipelineConfigFor("test"))

    and:
    taskExecutor.shutdown()

    expect:
    with(jobRegistry.getJob(jobNameFor(pipeline))) {
      name == jobNameFor(pipeline)
      restartable
    }
  }

  def "if a pipeline restarts it resumes from where it left off"() {
    given:
    def latch = new CountDownLatch(1)
    task1.execute(_) >> new DefaultTaskResult(SUCCEEDED)
    task2.execute(_) >> new DefaultTaskResult(RUNNING) >>
      new DefaultTaskResult(RUNNING) >>
      new DefaultTaskResult(RUNNING) >>
      {
//        taskExecutor.shutdown()
        latch.countDown()
        throw new RuntimeException()
      }

    and:
    def pipeline = pipelineStarter.start(pipelineConfigFor("test"))
    latch.await()

    when:
//    taskExecutor.initialize()
//
//    and:
    pipelineStarter.resume(pipeline)
    sleep 1000

    then:
    1 * task2.execute(_) >> new DefaultTaskResult(SUCCEEDED)
    0 * task1.execute(_)
  }

  private String pipelineConfigFor(String... stages) {
    def config = [
      application: "app",
      name       : "my-pipeline",
      stages     : stages.collect { [type: it] }
    ]
    mapper.writeValueAsString(config)
  }

  private String jobNameFor(Pipeline pipeline) {
    "Pipeline:${pipeline.application}:${pipeline.name}:${pipeline.id}"
  }

  @CompileStatic
  @Configuration
  static class AsyncJobLauncherConfiguration {
    @Bean TaskExecutor taskExecutor() {
      new ThreadPoolTaskExecutor(
        maxPoolSize: 1,
        corePoolSize: 1,
        waitForTasksToCompleteOnShutdown: false
      )
    }

    @Bean BatchConfigurer batchConfigurer(TaskExecutor taskExecutor) {
      // the base class is stupid and provides a factory method for
      // JobLauncher but makes it private see
      // https://github.com/spring-projects/spring-batch/pull/358
      new DefaultBatchConfigurer() {
        private JobLauncher jobLauncher

        @Override
        JobLauncher getJobLauncher() {
          jobLauncher
        }

        @Override
        void initialize() {
          super.initialize()

          jobLauncher = new SimpleJobLauncher(
            taskExecutor: taskExecutor,
            jobRepository: jobRepository
          )
        }
      }
    }
  }
}
