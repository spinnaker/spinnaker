package com.netflix.spinnaker.orca.restart

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.orca.DefaultTaskResult
import com.netflix.spinnaker.orca.Task
import com.netflix.spinnaker.orca.batch.StageBuilder
import com.netflix.spinnaker.orca.config.JesqueConfiguration
import com.netflix.spinnaker.orca.config.OrcaConfiguration
import com.netflix.spinnaker.orca.config.OrcaPersistenceConfiguration
import com.netflix.spinnaker.orca.pipeline.LinearStage
import com.netflix.spinnaker.orca.pipeline.PipelineStarter
import com.netflix.spinnaker.orca.pipeline.model.DefaultTask
import com.netflix.spinnaker.orca.pipeline.model.PipelineStage
import com.netflix.spinnaker.orca.pipeline.model.Stage
import com.netflix.spinnaker.orca.pipeline.persistence.ExecutionRepository
import com.netflix.spinnaker.orca.test.JobCompletionListener
import com.netflix.spinnaker.orca.test.TestConfiguration
import com.netflix.spinnaker.orca.test.batch.BatchTestConfiguration
import com.netflix.spinnaker.orca.test.redis.EmbeddedRedisConfiguration
import groovy.transform.CompileStatic
import org.springframework.batch.core.Step
import org.springframework.batch.core.configuration.JobRegistry
import org.springframework.batch.core.explore.JobExplorer
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.AnnotationConfigApplicationContext
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor
import spock.lang.AutoCleanup
import spock.lang.Specification
import static com.netflix.spinnaker.orca.ExecutionStatus.*
import static com.netflix.spinnaker.orca.pipeline.model.Stage.SyntheticStageOwner.STAGE_AFTER
import static com.netflix.spinnaker.orca.pipeline.model.Stage.SyntheticStageOwner.STAGE_BEFORE
import static java.lang.System.currentTimeMillis

class SyntheticStageRestartingSpec extends Specification {

  @AutoCleanup("destroy")
  def applicationContext = new AnnotationConfigApplicationContext()
  @Autowired ThreadPoolTaskExecutor taskExecutor
  @Autowired PipelineStarter pipelineStarter
  @Autowired ObjectMapper mapper
  @Autowired JobRegistry jobRegistry
  @Autowired JobExplorer jobExplorer
  @Autowired ExecutionRepository repository
  @Autowired JobCompletionListener jobCompletionListener

  def beforeTask = Mock(Task)
  def mainTask = Mock(Task)
  def afterTask = Mock(Task)

  def setup() {
    def beforeStage = new StandaloneStageBuilder("before", beforeTask)
    def afterStage = new StandaloneStageBuilder("after", afterTask)
    def testStage = new SimpleSyntheticStage("test", beforeStage, mainTask, afterStage)
    applicationContext.with {
      register(EmbeddedRedisConfiguration, JesqueConfiguration,
               BatchTestConfiguration, OrcaConfiguration, OrcaPersistenceConfiguration,
               JobCompletionListener, TestConfiguration)
      beanFactory.registerSingleton("testStage", testStage)
      beanFactory.registerSingleton("beforeStage", beforeStage)
      beanFactory.registerSingleton("afterStage", afterStage)
      refresh()

      [testStage, beforeStage, afterStage].each {
        beanFactory.autowireBean(it)
      }
      beanFactory.autowireBean(this)
    }
    [testStage, beforeStage, afterStage].each {
      it.applicationContext = applicationContext
    }
  }

  def cleanup() {
    applicationContext.destroy()
  }

  def "a previously run pipeline can be restarted and completed tasks are skipped"() {
    given:
    def pipeline = pipelineStarter.create(mapper.readValue(pipelineConfigFor("test"), Map))
    pipeline.stages[0].tasks << new DefaultTask(id: 2, name: "main", status: RUNNING,
                                                startTime: currentTimeMillis())
    pipeline.stages << new PipelineStage(pipeline, "before", "before", [:])
    pipeline.stages[1].id = pipeline.stages[0].id + "-1-before"
    pipeline.stages[1].syntheticStageOwner = STAGE_BEFORE
    pipeline.stages[1].parentStageId = pipeline.stages[0].id
    pipeline.stages[1].status = SUCCEEDED
    pipeline.stages[1].startTime = currentTimeMillis()
    pipeline.stages[1].endTime = currentTimeMillis()
    pipeline.stages[1].tasks << new DefaultTask(id: 2, name: "before", status: SUCCEEDED,
                                                startTime: currentTimeMillis(),
                                                endTime: currentTimeMillis())
    pipeline.stages << new PipelineStage(pipeline, "after", "after", [:])
    pipeline.stages[2].id = pipeline.stages[0].id + "-2-after"
    pipeline.stages[2].parentStageId = pipeline.stages[0].id
    pipeline.stages[2].syntheticStageOwner = STAGE_AFTER
    pipeline.stages[2].status = NOT_STARTED
    repository.store(pipeline)

    when:
    pipelineStarter.resume(pipeline)
    jobCompletionListener.await()

    then:
    repository.retrievePipeline(pipeline.id).status.toString() == SUCCEEDED.name()

    then:
    0 * beforeTask.execute(_)
    1 * mainTask.execute(_) >> new DefaultTaskResult(SUCCEEDED)
    1 * afterTask.execute(_) >> new DefaultTaskResult(SUCCEEDED)
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
  static class SimpleSyntheticStage extends LinearStage {

    private final Task task
    private final StageBuilder beforeStage
    private final StageBuilder afterStage

    SimpleSyntheticStage(String name, StageBuilder beforeStage, Task mainTask, StageBuilder afterStage) {
      super(name)
      this.task = mainTask
      this.beforeStage = beforeStage
      this.afterStage = afterStage
    }

    @Override
    public List<Step> buildSteps(Stage stage) {
      injectBefore(stage, "before", beforeStage, [:])
      injectAfter(stage, "after", afterStage, [:])
      [buildStep(stage, "main", task)]
    }
  }

  static class StandaloneStageBuilder extends LinearStage {
    private Task task

    StandaloneStageBuilder(String stageName, Task task) {
      super(stageName)
      this.task = task
    }

    @Override
    public List<Step> buildSteps(Stage stage) {
      return [buildStep(stage, type, task)]
    }
  }
}
