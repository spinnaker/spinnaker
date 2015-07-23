package com.netflix.spinnaker.orca.batch.persistence

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.kork.eureka.EurekaComponents
import com.netflix.spinnaker.orca.DefaultTaskResult
import com.netflix.spinnaker.orca.Task
import com.netflix.spinnaker.orca.config.JesqueConfiguration
import com.netflix.spinnaker.orca.config.OrcaConfiguration
import com.netflix.spinnaker.orca.config.OrcaPersistenceConfiguration
import com.netflix.spinnaker.orca.pipeline.LinearStage
import com.netflix.spinnaker.orca.pipeline.PipelineStarter
import com.netflix.spinnaker.orca.pipeline.model.DefaultTask
import com.netflix.spinnaker.orca.pipeline.model.Stage
import com.netflix.spinnaker.orca.pipeline.persistence.ExecutionRepository
import com.netflix.spinnaker.orca.test.TestLatch
import com.netflix.spinnaker.orca.test.batch.BatchTestConfiguration
import com.netflix.spinnaker.orca.test.redis.EmbeddedRedisConfiguration
import groovy.transform.CompileStatic
import org.springframework.batch.core.Step
import org.springframework.batch.core.configuration.JobRegistry
import org.springframework.batch.core.configuration.annotation.BatchConfigurer
import org.springframework.batch.core.configuration.annotation.DefaultBatchConfigurer
import org.springframework.batch.core.explore.JobExplorer
import org.springframework.batch.core.launch.JobLauncher
import org.springframework.batch.core.launch.support.SimpleJobLauncher
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.AnnotationConfigApplicationContext
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.task.TaskExecutor
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor
import spock.lang.Specification
import static com.netflix.spinnaker.orca.ExecutionStatus.RUNNING
import static com.netflix.spinnaker.orca.ExecutionStatus.SUCCEEDED

class PipelinePersistenceSpec extends Specification {

  def applicationContext = new AnnotationConfigApplicationContext()
  @Autowired ThreadPoolTaskExecutor taskExecutor
  @Autowired PipelineStarter pipelineStarter
  @Autowired ObjectMapper mapper
  @Autowired JobRegistry jobRegistry
  @Autowired JobExplorer jobExplorer
  @Autowired ExecutionRepository repository

  def task1 = Mock(Task)
  def task2 = Mock(Task)

  def setup() {
    def testStage = new AutowiredTestStage("test", task1, task2)
    applicationContext.with {
      register(EmbeddedRedisConfiguration, JesqueConfiguration, EurekaComponents,
               AsyncJobLauncherConfiguration,
               BatchTestConfiguration, OrcaConfiguration, OrcaPersistenceConfiguration)
      beanFactory.registerSingleton("testStage", testStage)
      refresh()

      beanFactory.autowireBean(testStage)
      beanFactory.autowireBean(this)
    }
    testStage.applicationContext = applicationContext
  }

  def "if a pipeline restarts it resumes from where it left off"() {
    given:
    def latch = new TestLatch(1)
    task1.execute(_) >> new DefaultTaskResult(SUCCEEDED)
    task2.execute(_) >> {
      try {
        new DefaultTaskResult(RUNNING)
      } finally {
        latch.countDown()
      }
    } >> {
      try {
        new DefaultTaskResult(SUCCEEDED)
      } finally {
        latch.countDown()
      }
    }

    and:
    def pipeline = pipelineStarter.start(pipelineConfigFor("test"))
    latch.await()

    and:
    taskExecutor.shutdown()
    taskExecutor.initialize()
    latch.reset()

    when:
    pipelineStarter.resume(pipeline)
    latch.await()

    then:
    0 * task1.execute(_)
  }

  def "a previously run pipeline can be restarted and completed tasks are skipped"() {
    given:
    def latch = new TestLatch(1)

    and:
    def pipeline = pipelineStarter.create(mapper.readValue(pipelineConfigFor("test"), Map))
    pipeline.stages[0].tasks << new DefaultTask(id: 2, name: "task1", status: SUCCEEDED,
                                                startTime: System.currentTimeMillis(),
                                                endTime: System.currentTimeMillis())
    pipeline.stages[0].tasks << new DefaultTask(id: 3, name: "task2", status: RUNNING,
                                                startTime: System.currentTimeMillis())
    repository.store(pipeline)

    when:
    pipelineStarter.resume(pipeline)
    latch.await()

    then:
    0 * task1.execute(_)
    1 * task2.execute(_) >> {
      try {
        new DefaultTaskResult(SUCCEEDED)
      } finally {
        latch.countDown()
      }
    }
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
  @Configuration
  static class AsyncJobLauncherConfiguration {
    @Bean TaskExecutor taskExecutor() {
      new ThreadPoolTaskExecutor(
        maxPoolSize: 1,
        corePoolSize: 1,
        waitForTasksToCompleteOnShutdown: false,
        awaitTerminationSeconds: 3
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

  @CompileStatic
  class AutowiredTestStage extends LinearStage {

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
