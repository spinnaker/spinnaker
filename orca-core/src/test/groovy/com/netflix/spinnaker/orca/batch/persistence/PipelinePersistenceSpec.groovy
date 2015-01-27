package com.netflix.spinnaker.orca.batch.persistence

import groovy.transform.CompileStatic
import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.orca.DefaultTaskResult
import com.netflix.spinnaker.orca.Task
import com.netflix.spinnaker.orca.batch.pipeline.TestStage
import com.netflix.spinnaker.orca.config.OrcaConfiguration
import com.netflix.spinnaker.orca.pipeline.PipelineStarter
import com.netflix.spinnaker.orca.pipeline.persistence.ExecutionRepository
import com.netflix.spinnaker.orca.test.batch.BatchTestConfiguration
import org.springframework.batch.core.configuration.JobRegistry
import org.springframework.batch.core.configuration.annotation.BatchConfigurer
import org.springframework.batch.core.configuration.annotation.DefaultBatchConfigurer
import org.springframework.batch.core.configuration.annotation.StepBuilderFactory
import org.springframework.batch.core.launch.JobLauncher
import org.springframework.batch.core.launch.support.SimpleJobLauncher
import org.springframework.batch.core.repository.JobRepository
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
import static org.springframework.test.annotation.DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD

@ContextConfiguration(classes = [
    AsyncJobLauncherConfiguration,
    BatchTestConfiguration,
    OrcaConfiguration
])
@DirtiesContext(classMode = AFTER_EACH_TEST_METHOD)
class PipelinePersistenceSpec extends Specification {

  @Autowired ThreadPoolTaskExecutor taskExecutor
  @Autowired JobLauncher jobLauncher
  @Autowired PipelineStarter pipelineStarter
  @Autowired JobRepository jobRepository
  @Autowired AbstractApplicationContext applicationContext
  @Autowired StepBuilderFactory steps
  @Autowired ExecutionRepository executionRepository
  @Autowired ObjectMapper mapper
  @Autowired JobRegistry jobRegistry

  def task = Mock(Task)

  def setup() {
    def testStage = new TestStage("test", steps, executionRepository, task)
    applicationContext.beanFactory.registerSingleton("testStage", testStage)

    pipelineStarter.initialize()
  }

  def "if a pipeline dies we can reconstitute it"() {
    given:
    task.execute(_) >> new DefaultTaskResult(RUNNING)

    and:
    def config = [
        application: "app",
        stages     : [
            [type: "test"]
        ]
    ]
    def configJson = mapper.writeValueAsString(config)
    def pipeline = pipelineStarter.start(configJson)

    and:
    sleep 1000
    taskExecutor.shutdown()

    expect:
    jobRegistry.getJob("Pipeline:${pipeline.application}:${pipeline.name}:${pipeline.id}")
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
