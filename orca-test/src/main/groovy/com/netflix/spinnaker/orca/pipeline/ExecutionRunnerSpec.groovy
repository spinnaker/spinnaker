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

package com.netflix.spinnaker.orca.pipeline

import com.netflix.spinnaker.orca.pipeline.util.ContextParameterProcessor

import java.util.function.BiFunction
import java.util.function.Consumer
import com.netflix.spinnaker.orca.Task
import com.netflix.spinnaker.orca.TaskResult
import com.netflix.spinnaker.orca.listeners.ExecutionListener
import com.netflix.spinnaker.orca.listeners.StageListener
import com.netflix.spinnaker.orca.pipeline.model.Execution
import com.netflix.spinnaker.orca.pipeline.model.Pipeline
import com.netflix.spinnaker.orca.pipeline.model.Stage
import com.netflix.spinnaker.orca.pipeline.model.SyntheticStageOwner
import com.netflix.spinnaker.orca.pipeline.parallel.WaitForRequisiteCompletionStage
import com.netflix.spinnaker.orca.pipeline.parallel.WaitForRequisiteCompletionTask
import com.netflix.spinnaker.orca.pipeline.persistence.ExecutionRepository
import com.netflix.spinnaker.orca.pipeline.util.StageNavigator
import groovy.transform.CompileStatic
import org.spockframework.spring.xml.SpockMockFactoryBean
import org.springframework.beans.factory.FactoryBean
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.context.annotation.Bean
import org.springframework.context.support.GenericApplicationContext
import org.springframework.test.annotation.DirtiesContext
import org.springframework.test.context.ContextConfiguration
import spock.lang.Issue
import spock.lang.Specification
import spock.lang.Subject
import spock.lang.Unroll
import static com.netflix.spinnaker.orca.ExecutionStatus.*
import static com.netflix.spinnaker.orca.pipeline.StageDefinitionBuilder.StageDefinitionBuilderSupport.getType
import static com.netflix.spinnaker.orca.pipeline.StageDefinitionBuilder.StageDefinitionBuilderSupport.newStage
import static com.netflix.spinnaker.orca.pipeline.TaskNode.GraphType.FULL
import static com.netflix.spinnaker.orca.pipeline.TaskNode.TaskDefinition
import static com.netflix.spinnaker.orca.pipeline.model.SyntheticStageOwner.STAGE_AFTER
import static com.netflix.spinnaker.orca.pipeline.model.SyntheticStageOwner.STAGE_BEFORE
import static org.hamcrest.Matchers.containsInAnyOrder
import static org.springframework.test.annotation.DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD
import static spock.util.matcher.HamcrestSupport.expect

@ContextConfiguration(classes = [
  StageNavigator, WaitForRequisiteCompletionTask, Config,
  WaitForRequisiteCompletionStage, RestrictExecutionDuringTimeWindow
])
@DirtiesContext(classMode = AFTER_EACH_TEST_METHOD)
abstract class ExecutionRunnerSpec<R extends ExecutionRunner> extends Specification {

  abstract R create(StageDefinitionBuilder... stageDefBuilders)

  @Autowired GenericApplicationContext applicationContext

  @Autowired ExecutionRepository executionRepository

  @Autowired TestTask testTask
  @Autowired PreLoopTask preLoopTask
  @Autowired StartLoopTask startLoopTask
  @Autowired EndLoopTask endLoopTask
  @Autowired PostLoopTask postLoopTask
  @Autowired @Qualifier("stageListener") StageListener stageListener
  @Autowired @Qualifier("executionListener") ExecutionListener executionListener

  def "throws an exception if there's no builder for a stage type"() {
    given:
    def stageDefBuilder = Stub(StageDefinitionBuilder) {
      getType() >> stageType.reverse()
    }
    @Subject def runner = create(stageDefBuilder)

    when:
    runner.start(execution)

    then:
    thrown ExecutionRunner.NoSuchStageDefinitionBuilder

    where:
    stageType = "foo"
    execution = Pipeline.builder().withStage(stageType).build()
  }

  def "builds tasks for each stage"() {
    given:
    def stageDefBuilder = Stub(StageDefinitionBuilder) {
      getType() >> stageType
      buildTaskGraph(_) >> new TaskNode.TaskGraph(FULL, [new TaskDefinition("1", Task)])
    }
    @Subject def runner = create(stageDefBuilder)

    when:
    runner.start(execution)

    then:
    with(execution.stages) {
      tasks.id.flatten() == ["1"]
    }

    where:
    stageType = "foo"
    execution = Pipeline.builder().withStage(stageType).build()
  }

  @Unroll
  def "marks start and end of each stage when there are #numTasks tasks"() {
    given:
    def stageDefBuilder = Stub(StageDefinitionBuilder) {
      getType() >> stageType
      buildTaskGraph(_) >> new TaskNode.TaskGraph(FULL, (1..numTasks).collect { i -> new TaskDefinition("$i", Task) })
    }
    @Subject def runner = create(stageDefBuilder)

    when:
    runner.start(execution)

    then:
    with(execution.stages.first()) {
      tasks.head().stageStart
      tasks.tail().every { !it.stageStart }
      tasks.reverse().head().stageEnd
      tasks.reverse().tail().every { !it.stageEnd }
    }

    where:
    stageType = "foo"
    execution = Pipeline.builder().withStage(stageType).build()
    numTasks << [1, 2, 3]
  }

  def "builds each pre-stage"() {
    given:
    def stageDefBuilders = stageTypes.collect { stageType ->
      def preStage1 = before(new Stage<>(execution, "${stageType}_pre1"))
      def preStage2 = before(new Stage<>(execution, "${stageType}_pre2"))
      [
        Stub(StageDefinitionBuilder) {
          getType() >> stageType
          buildTaskGraph() >> new TaskNode.TaskGraph(FULL, [new TaskDefinition("${stageType}_1", Task)])
          aroundStages(_) >> [preStage1, preStage2]
        },
        Stub(StageDefinitionBuilder) {
          getType() >> "${stageType}_pre1"
        },
        Stub(StageDefinitionBuilder) {
          getType() >> "${stageType}_pre2"
        }
      ]
    }
    .flatten()
    @Subject def runner = create(*stageDefBuilders)

    when:
    runner.start(execution)

    then:
    execution.stages.type == stageTypes.collect { stageType ->
      ["${stageType}_pre1", "${stageType}_pre2", stageType]
    }.flatten()

    where:
    stageTypes = ["foo", "bar"]
    execution = Pipeline.builder().withStages(*stageTypes).build()
  }

  def "builds each post-stage"() {
    given:
    def stageDefBuilders = stageTypes.collect { stageType ->
      def postStage1 = after(new Stage<>(execution, "${stageType}_post1"))
      def postStage2 = after(new Stage<>(execution, "${stageType}_post2"))
      [
        Stub(StageDefinitionBuilder) {
          getType() >> stageType
          buildTaskGraph() >> new TaskNode.TaskGraph(FULL, [new TaskDefinition("${stageType}_1", Task)])
          aroundStages(_) >> [postStage1, postStage2]
        },
        Stub(StageDefinitionBuilder) {
          getType() >> "${stageType}_post1"
        },
        Stub(StageDefinitionBuilder) {
          getType() >> "${stageType}_post2"
        }
      ]
    }
    .flatten()
    @Subject def runner = create(*stageDefBuilders)

    when:
    runner.start(execution)

    then:
    execution.stages.type == stageTypes.collect { stageType ->
      [stageType, "${stageType}_post1", "${stageType}_post2"]
    }.flatten()

    where:
    stageTypes = ["foo", "bar"]
    execution = Pipeline.builder().withStages(*stageTypes).build()
  }

  def "builds tasks for pre and post-stages"() {
    given:
    def preStage = before(new Stage<>(execution, "${stageType}_pre"))
    def postStage = after(new Stage<>(execution, "${stageType}_post"))
    def stageDefBuilder = Stub(StageDefinitionBuilder) {
      getType() >> stageType
      buildTaskGraph(_) >> new TaskNode.TaskGraph(FULL, [new TaskDefinition("${stageType}_1", Task)])
      aroundStages(_) >> [preStage, postStage]
    }
    def preStageDefBuilder = Stub(StageDefinitionBuilder) {
      getType() >> "${stageType}_pre"
      buildTaskGraph(_) >> new TaskNode.TaskGraph(FULL, [new TaskDefinition("${stageType}_pre_1", Task)])
    }
    def postStageDefBuilder = Stub(StageDefinitionBuilder) {
      getType() >> "${stageType}_post"
      buildTaskGraph(_) >> new TaskNode.TaskGraph(FULL, [new TaskDefinition("${stageType}_post_1", Task)])
    }
    @Subject def runner = create(stageDefBuilder, preStageDefBuilder, postStageDefBuilder)

    when:
    runner.start(execution)

    then:
    with(execution.stages) {
      tasks.name.flatten() == ["${stageType}_pre_1", "${stageType}_1", "${stageType}_post_1"]
    }

    where:
    stageType = "foo"
    execution = Pipeline.builder().withStages(stageType).build()
  }

  @Unroll
  def "runs a single step in in #description mode"() {
    given:
    execution.stages[0].requisiteStageRefIds = []
    executionRepository.retrievePipeline(execution.id) >> execution

    and:
    def stageDefinitionBuilder = Stub(StageDefinitionBuilder) {
      getType() >> stageType
      buildTaskGraph(_) >> new TaskNode.TaskGraph(FULL, [new TaskDefinition("test", TestTask)])
    }
    @Subject runner = create(stageDefinitionBuilder)

    when:
    runner.start(execution)

    then:
    1 * testTask.execute(_) >> new TaskResult(SUCCEEDED)

    where:
    parallel | description
    true     | "parallel"

    stageType = "foo"
    execution = Pipeline.builder().withId("1").withStage(stageType).withParallel(parallel).build()
  }

  def "runs synthetic stages"() {
    given:
    execution.stages[0].requisiteStageRefIds = []
    executionRepository.retrievePipeline(execution.id) >> execution

    and:
    def stageDefinitionBuilders = [
      Stub(StageDefinitionBuilder) {
        getType() >> stageType
        buildTaskGraph(_) >> new TaskNode.TaskGraph(FULL, [new TaskDefinition("test", TestTask)])
        aroundStages(_) >> { Stage<Pipeline> parentStage ->
          [
            newStage(execution, "before_${stageType}_1", "before", [:], parentStage, STAGE_BEFORE),
            newStage(execution, "before_${stageType}_2", "before", [:], parentStage, STAGE_BEFORE),
            newStage(execution, "after_${stageType}_1", "after", [:], parentStage, STAGE_AFTER),
            newStage(execution, "after_${stageType}_2", "after", [:], parentStage, STAGE_AFTER)
          ]
        }
      },
      Stub(StageDefinitionBuilder) {
        getType() >> "before_${stageType}_1"
        buildTaskGraph(_) >> new TaskNode.TaskGraph(FULL, [new TaskDefinition("before_test_1", TestTask)])
      },
      Stub(StageDefinitionBuilder) {
        getType() >> "before_${stageType}_2"
        buildTaskGraph(_) >> new TaskNode.TaskGraph(FULL, [new TaskDefinition("before_test_2", TestTask)])
      },
      Stub(StageDefinitionBuilder) {
        getType() >> "after_${stageType}_1"
        buildTaskGraph(_) >> new TaskNode.TaskGraph(FULL, [new TaskDefinition("after_test_1", TestTask)])
      },
      Stub(StageDefinitionBuilder) {
        getType() >> "after_${stageType}_2"
        buildTaskGraph(_) >> new TaskNode.TaskGraph(FULL, [new TaskDefinition("after_test_2", TestTask)])
      }
    ]
    @Subject runner = create(*stageDefinitionBuilders)

    and:
    def executedStageTypes = []
    testTask.execute(_) >> { Stage stage ->
      executedStageTypes << stage.type
      new TaskResult(SUCCEEDED)
    }

    when:
    runner.start(execution)

    then:
    executedStageTypes == ["before_${stageType}_1", "before_${stageType}_2", stageType, "after_${stageType}_1", "after_${stageType}_2"]

    where:
    stageType = "foo"
    execution = Pipeline.builder().withId("1").withStage(stageType).withParallel(true).build()
  }

  def "runs synthetic stages that have their own synthetic stages"() {
    given:
    execution.stages[0].requisiteStageRefIds = []
    executionRepository.retrievePipeline(execution.id) >> execution

    and:
    def stageDefinitionBuilders = [
      Stub(StageDefinitionBuilder) {
        getType() >> stageType
        buildTaskGraph(_) >> new TaskNode.TaskGraph(FULL, [new TaskDefinition("task", TestTask)])
        aroundStages(_) >> { Stage<Pipeline> parentStage ->
          [
            newStage(execution, "after_$stageType", "after", [:], parentStage, STAGE_AFTER)
          ]
        }
      },
      Stub(StageDefinitionBuilder) {
        getType() >> "before_after_${stageType}"
        buildTaskGraph(_) >> new TaskNode.TaskGraph(FULL, [new TaskDefinition("before_after_task", TestTask)])
      },
      Stub(StageDefinitionBuilder) {
        getType() >> "after_after_${stageType}"
        buildTaskGraph(_) >> new TaskNode.TaskGraph(FULL, [new TaskDefinition("after_after_task", TestTask)])
      },
      Stub(StageDefinitionBuilder) {
        getType() >> "after_$stageType"
        buildTaskGraph(_) >> new TaskNode.TaskGraph(FULL, [new TaskDefinition("after_task", TestTask)])
        aroundStages(_) >> { Stage<Pipeline> parentStage ->
          [
            newStage(execution, "before_after_${stageType}", "before_after", [:], parentStage, STAGE_BEFORE),
            newStage(execution, "after_after_$stageType", "after_after", [:], parentStage, STAGE_AFTER)
          ]
        }
      }
    ]
    @Subject runner = create(*stageDefinitionBuilders)

    and:
    def executedStageTypes = []
    testTask.execute(_) >> { Stage stage ->
      executedStageTypes << stage.type
      new TaskResult(SUCCEEDED)
    }

    when:
    runner.start(execution)

    then:
    executedStageTypes == [stageType, "before_after_${stageType}", "after_$stageType", "after_after_$stageType"]

    where:
    stageType = "foo"
    execution = Pipeline.builder().withId("1").withStage(stageType).withParallel(true).build()
  }

  def "executes stage graph in the correct order"() {
    given:
    def startStage = new Stage<>(execution, "start")
    def branchAStage = new Stage<>(execution, "branchA")
    def branchBStage = new Stage<>(execution, "branchB")
    def endStage = new Stage<>(execution, "end")

    startStage.refId = "1"
    branchAStage.refId = "2"
    branchBStage.refId = "3"
    endStage.refId = "4"

    startStage.requisiteStageRefIds = []
    branchAStage.requisiteStageRefIds = [startStage.refId]
    branchBStage.requisiteStageRefIds = [startStage.refId]
    endStage.requisiteStageRefIds = [branchAStage.refId, branchBStage.refId]

    execution.stages << startStage
    execution.stages << endStage
    execution.stages << branchBStage
    execution.stages << branchAStage

    executionRepository.retrievePipeline(execution.id) >> execution

    and:
    def startStageDefinitionBuilder = stageDefinition(startStage.type) { builder -> builder.withTask("test", TestTask) }
    def branchAStageDefinitionBuilder = Stub(StageDefinitionBuilder) {
      getType() >> branchAStage.type
      buildTaskGraph(_) >> new TaskNode.TaskGraph(FULL, [new TaskDefinition("test", TestTask)])
    }
    def branchBStageDefinitionBuilder = Stub(StageDefinitionBuilder) {
      getType() >> branchBStage.type
      buildTaskGraph(_) >> new TaskNode.TaskGraph(FULL, [new TaskDefinition("test", TestTask)])
    }
    def endStageDefinitionBuilder = Stub(StageDefinitionBuilder) {
      getType() >> endStage.type
      buildTaskGraph(_) >> new TaskNode.TaskGraph(FULL, [new TaskDefinition("test", TestTask)])
    }
    @Subject
      runner = create(startStageDefinitionBuilder, branchAStageDefinitionBuilder, branchBStageDefinitionBuilder, endStageDefinitionBuilder)

    and:
    def executedStageTypes = []
    testTask.execute(_) >> { Stage stage ->
      executedStageTypes << stage.type
      new TaskResult(SUCCEEDED)
    }

    when:
    runner.start(execution)

    then:
    expect executedStageTypes, containsInAnyOrder(startStage.type, branchAStage.type, branchBStage.type, endStage.type)
    executedStageTypes.first() == startStage.type
    executedStageTypes.last() == endStage.type

    where:
    execution = Pipeline.builder().withId("1").withParallel(true).build()
  }

  @Issue("SPIN-2165")
  def "executes pipelines with multiple 'root' stages"() {
    given:
    def branchAStage = new Stage<>(execution, "branchA")
    def branchBStage = new Stage<>(execution, "branchB")
    def endStage = new Stage<>(execution, "end")

    branchAStage.refId = "1"
    branchBStage.refId = "2"
    endStage.refId = "3"

    branchAStage.requisiteStageRefIds = []
    branchBStage.requisiteStageRefIds = []
    endStage.requisiteStageRefIds = [branchAStage.refId, branchBStage.refId]

    execution.stages << endStage
    execution.stages << branchBStage
    execution.stages << branchAStage

    executionRepository.retrievePipeline(execution.id) >> execution

    and:
    def branchAStageDefinitionBuilder = Stub(StageDefinitionBuilder) {
      getType() >> branchAStage.type
      buildTaskGraph(_) >> new TaskNode.TaskGraph(FULL, [new TaskDefinition("test", TestTask)])
    }
    def branchBStageDefinitionBuilder = Stub(StageDefinitionBuilder) {
      getType() >> branchBStage.type
      buildTaskGraph(_) >> new TaskNode.TaskGraph(FULL, [new TaskDefinition("test", TestTask)])
    }
    def endStageDefinitionBuilder = Stub(StageDefinitionBuilder) {
      getType() >> endStage.type
      buildTaskGraph(_) >> new TaskNode.TaskGraph(FULL, [new TaskDefinition("test", TestTask)])
    }
    @Subject runner = create(branchAStageDefinitionBuilder, branchBStageDefinitionBuilder, endStageDefinitionBuilder)

    and:
    def executedStageTypes = []
    testTask.execute(_) >> { Stage stage ->
      executedStageTypes << stage.type
      new TaskResult(SUCCEEDED)
    }

    when:
    runner.start(execution)

    then:
    expect executedStageTypes, containsInAnyOrder(branchAStage.type, branchBStage.type, endStage.type)
    executedStageTypes.last() == endStage.type

    where:
    execution = Pipeline.builder().withId("1").withParallel(true).build()
  }

  def "executes loops"() {
    given:
    def stage = new Stage<>(execution, "looping")
    execution.stages << stage

    executionRepository.retrievePipeline(execution.id) >> execution

    and:
    def stageDefinitionBuilder = stageDefinition("looping") { builder ->
      builder
        .withTask("preLoop", PreLoopTask)
        .withLoop({ subGraph ->
        subGraph
          .withTask("startLoop", StartLoopTask)
          .withTask("endLoop", EndLoopTask)
      })
        .withTask("postLoop", PostLoopTask)
    }
    @Subject runner = create(stageDefinitionBuilder)

    when:
    runner.start(execution)

    then:
    1 * preLoopTask.execute(_) >> new TaskResult(SUCCEEDED)
    3 * startLoopTask.execute(_) >> new TaskResult(SUCCEEDED)
    3 * endLoopTask.execute(_) >> new TaskResult(REDIRECT) >> new TaskResult(REDIRECT) >> new TaskResult(SUCCEEDED)
    1 * postLoopTask.execute(_) >> new TaskResult(SUCCEEDED)

    where:
    execution = Pipeline.builder().withId("1").withParallel(true).build()
  }

  def "looping stages can update context"() {
    given:
    def stage = new Stage<>(execution, "looping")
    execution.stages << stage

    executionRepository.retrievePipeline(execution.id) >> execution

    and:
    def stageDefinitionBuilder = stageDefinition("looping") { builder ->
      builder
        .withTask("preLoop", PreLoopTask)
        .withLoop({ subGraph ->
        subGraph
          .withTask("startLoop", StartLoopTask)
          .withTask("endLoop", EndLoopTask)
      })
        .withTask("postLoop", PostLoopTask)
    }
    @Subject runner = create(stageDefinitionBuilder)

    and:
    def next = 1
    preLoopTask.execute(_) >> new TaskResult(SUCCEEDED)
    startLoopTask.execute(_) >> { Stage<?> s ->
      def values = s.context.values
      if (values == null) values = []
      values += next
      next++
      new TaskResult(SUCCEEDED, [values: values])
    }
    endLoopTask.execute(_) >> new TaskResult(REDIRECT) >> new TaskResult(REDIRECT) >> new TaskResult(SUCCEEDED)
    postLoopTask.execute(_) >> new TaskResult(SUCCEEDED)

    when:
    runner.start(execution)

    then:
    execution.stages.find { it.type == "looping" }.context.values == [1, 2, 3]

    where:
    execution = Pipeline.builder().withId("1").withParallel(true).build()
  }

  def "loop tasks are left in SUCCEEDED state on completion of the loop"() {
    given:
    def stage = new Stage<>(execution, "looping")
    execution.stages << stage

    executionRepository.retrievePipeline(execution.id) >> execution

    and:
    def stageDefinitionBuilder = stageDefinition("looping") { builder ->
      builder
        .withTask("preLoop", PreLoopTask)
        .withLoop({ subGraph ->
        subGraph
          .withTask("startLoop", StartLoopTask)
          .withTask("endLoop", EndLoopTask)
      })
        .withTask("postLoop", PostLoopTask)
    }
    @Subject runner = create(stageDefinitionBuilder)

    and:
    preLoopTask.execute(_) >> new TaskResult(SUCCEEDED)
    startLoopTask.execute(_) >> new TaskResult(SUCCEEDED)
    endLoopTask.execute(_) >> new TaskResult(REDIRECT) >> new TaskResult(REDIRECT) >> new TaskResult(SUCCEEDED)
    postLoopTask.execute(_) >> new TaskResult(SUCCEEDED)

    when:
    runner.start(execution)

    then:
    execution
      .stages
      .find { it.type == "looping" }
      .tasks
      .status
      .every { it == SUCCEEDED }

    where:
    execution = Pipeline.builder().withId("1").withParallel(true).build()
  }

  def "executes stages with internal parallel branches"() {
    given:
    def stage = new Stage<>(execution, "branching")
    execution.stages << stage

    executionRepository.retrievePipeline(execution.id) >> execution

    and:
    def stageDefinitionBuilder = stageDefinition("branching", contexts, { builder ->
      builder.withTask("start", StartLoopTask)
    }, { builder ->
      builder.withTask("branch", TestTask)
    }, { builder ->
      builder.withTask("end", EndLoopTask)
    })

    @Subject runner = create(stageDefinitionBuilder)

    when:
    runner.start(execution)

    then:
    1 * startLoopTask.execute(_) >> new TaskResult(SUCCEEDED)

    then:
    2 * testTask.execute(_) >> new TaskResult(SUCCEEDED)

    then:
    1 * endLoopTask.execute(_) >> new TaskResult(SUCCEEDED)

    where:
    execution = Pipeline.builder().withId("1").withParallel(true).build()
    contexts = [[region: "a"], [region: "b"]]
  }

  def "does not consider before tasks of an internal parallel stage to complete the stage"() {
    given:
    def stage = new Stage<>(execution, "branching")
    execution.stages << stage

    executionRepository.retrievePipeline(execution.id) >> execution

    and:
    def stageDefinitionBuilder = stageDefinition("branching", contexts, { builder ->
      builder.withTask("start", StartLoopTask)
    }, { builder ->
      builder.withTask("branch", TestTask)
    }, { builder ->
      builder.withTask("end", EndLoopTask)
    })

    @Subject runner = create(stageDefinitionBuilder)

    when:
    runner.start(execution)

    then:
    with(stage.tasks) {
      size() == 2
      first().stageStart
      !first().stageEnd
      !last().stageStart
      last().stageEnd
    }

    where:
    execution = Pipeline.builder().withId("1").withParallel(true).build()
    contexts = [[region: "a"], [region: "b"]]
  }

  def "applies execution windows correctly to internal parallel stages"() {
    given:
    def stage = new Stage<>(execution, "branching")
    stage.initializationStage = true
    stage.context.restrictExecutionDuringTimeWindow = true
    stage.context.restrictedExecutionWindow = [
      whitelist        : [[startHour: 0, startMin: 0, endHour: 23, endMin: 59]],
      days             : [1, 2, 3, 4, 5, 6, 7],
      skipRemainingWait: true
    ]
    execution.stages << stage

    executionRepository.retrievePipeline(execution.id) >> execution

    and:
    def stageDefinitionBuilder = stageDefinition("branching", contexts, { builder ->
      builder.withTask("start", StartLoopTask)
    }, { builder ->
      builder.withTask("branch", TestTask)
    }, { builder ->
      builder.withTask("end", EndLoopTask)
    })

    @Subject runner = create(stageDefinitionBuilder)

    and:
    startLoopTask.execute(_) >> new TaskResult(SUCCEEDED)
    testTask.execute(_) >> new TaskResult(SUCCEEDED)
    endLoopTask.execute(_) >> new TaskResult(SUCCEEDED)

    when:
    runner.start(execution)

    then:
    execution.stages.findAll {
      it.type == RestrictExecutionDuringTimeWindow.TYPE
    }.size() == 1

    where:
    execution = Pipeline.builder().withId("1").withParallel(true).build()
    contexts = [[region: "a"], [region: "b"]]
  }

  def "if a stage is allowed to fail it is marked as FAILED_CONTINUE"() {
    given:
    def stage = new Stage<>(execution, stageType, [continuePipeline: true])
    execution.stages << stage

    and:
    executionRepository.retrievePipeline(execution.id) >> execution

    and:
    def stageDefinitionBuilder = stageDefinition(stageType) { builder ->
      builder.withTask("fails", TestTask)
    }
    @Subject runner = create(stageDefinitionBuilder)

    and:
    testTask.execute(_) >> new TaskResult(TERMINAL)

    when:
    runner.start(execution)

    then:
    1 * executionRepository.updateStatus(execution.id, SUCCEEDED)

    and:
    execution.stages.first().status == FAILED_CONTINUE

    where:
    stageType = "canfail"
    execution = Pipeline.builder().withId("1").withParallel(true).build()
  }

  @Issue("SPIN-2122")
  def "a stage is still considered FAILED_CONTINUE if a task downstream of the failed one succeeds"() {
    given:
    def stage = new Stage<>(execution, stageType, [continuePipeline: true])
    execution.stages << stage

    and:
    executionRepository.retrievePipeline(execution.id) >> execution

    and:
    def stageDefinitionBuilder = stageDefinition(stageType) { builder ->
      builder.withTask("fails", StartLoopTask)
      builder.withTask("succeeds", EndLoopTask)
    }
    @Subject runner = create(stageDefinitionBuilder)

    and:
    startLoopTask.execute(_) >> new TaskResult(TERMINAL)
    endLoopTask.execute(_) >> new TaskResult(SUCCEEDED)

    when:
    runner.start(execution)

    then:
    1 * executionRepository.updateStatus(execution.id, SUCCEEDED)

    and:
    execution.stages.first().status == FAILED_CONTINUE

    where:
    stageType = "canfail"
    execution = Pipeline.builder().withId("1").withParallel(true).build()
  }

  @Unroll
  def "parallel stages can optionally rename the base stage"() {
    given:
    def stage = new Stage<>(execution, "branching", "branching", [:])
    execution.stages << stage

    executionRepository.retrievePipeline(execution.id) >> execution

    and:
    testTask.execute(_) >> new TaskResult(SUCCEEDED)

    and:
    def stageDefinitionBuilder = stageDefinition("branching", contexts, noOp(), { builder ->
      builder.withTask("branch", TestTask)
    }, noOp(), { _, hasParallelFlows ->
      hasParallelFlows ? "branching - parallel" : "branching"
    })

    @Subject runner = create(stageDefinitionBuilder)

    when:
    runner.start(execution)

    then:
    execution.stages.head().name == expectedBaseStageName
    execution.stages.tail().every {
      it.name == expectedParallelStageName
    }

    where:
    contexts                       | expectedBaseStageName  | expectedParallelStageName
    [[region: "a"]]                | "branching"            | "branching"
    [[region: "a"], [region: "b"]] | "branching - parallel" | "branching"

    execution = Pipeline.builder().withId("1").withParallel(true).build()
  }

  def "listeners are triggered around each task and stage"() {
    given:
    execution.stages[0].requisiteStageRefIds = []
    executionRepository.retrievePipeline(execution.id) >> execution

    and:
    def stageDefinitionBuilder = Stub(StageDefinitionBuilder) {
      getType() >> stageType
      buildTaskGraph(_) >> new TaskNode.TaskGraph(FULL, [new TaskDefinition("test", TestTask)])
    }
    @Subject runner = create(stageDefinitionBuilder)

    and:
    testTask.execute(_) >> new TaskResult(SUCCEEDED)

    when:
    runner.start(execution)

    then:
    1 * stageListener.beforeStage(*_)

    then:
    1 * stageListener.beforeTask(*_)

    then:
    1 * stageListener.afterTask(*_)

    then:
    1 * stageListener.afterStage(*_)

    where:
    stageType = "foo"
    execution = Pipeline.builder().withId("1").withStage(stageType).withParallel(true).build()
  }

  def "wires in a waitForExecutionWindow stage if necessary"() {
    given:
    execution.stages[0].requisiteStageRefIds = []
    executionRepository.retrievePipeline(execution.id) >> execution

    and:
    def stageDefinitionBuilder = Stub(StageDefinitionBuilder) {
      getType() >> stageType
      buildTaskGraph(_) >> new TaskNode.TaskGraph(FULL, [new TaskDefinition("test", TestTask)])
    }
    @Subject runner = create(stageDefinitionBuilder)

    and:
    testTask.execute(_) >> new TaskResult(SUCCEEDED)

    when:
    runner.start(execution)

    then:
    execution.stages.size() == old(execution.stages.size()) + 1
    execution.stages.first().type == RestrictExecutionDuringTimeWindow.TYPE

    where:
    stageType = "foo"
    execution = Pipeline
      .builder()
      .withId("1")
      .withStage(stageType, stageType, [restrictExecutionDuringTimeWindow: true])
      .withParallel(true).build()
  }

  @Issue("SPIN-2238")
  // Stages such as Canary with no tasks of their own (only synthetic stages)
  // failed to join correctly as they remain NOT_STARTED
  def "handles joins for parallel no-op stages"() {
    given:
    execution.with {
      stages[0].refId = "1"
      stages[0].requisiteStageRefIds = []

      stages[1].refId = "2"
      stages[1].requisiteStageRefIds = []

      stages[2].refId = "3"
      stages[2].requisiteStageRefIds = stages[0..1].refId
    }

    and:
    executionRepository.retrievePipeline(execution.id) >> execution

    and:
    def noOpStageDefinitionBuilder = Stub(StageDefinitionBuilder) {
      getType() >> stageType
      buildTaskGraph(_) >> TaskNode.emptyGraph(FULL)
    }
    def joinStageDefinitionBuilder = Stub(StageDefinitionBuilder) {
      getType() >> "join"
      buildTaskGraph(_) >> TaskNode.singleton(FULL, "test", TestTask)
    }
    @Subject runner = create(noOpStageDefinitionBuilder, joinStageDefinitionBuilder)

    when:
    runner.start(execution)

    then:
    1 * testTask.execute(_) >> new TaskResult(SUCCEEDED)

    where:
    stageType = "noop"
    execution = Pipeline
      .builder()
      .withId("1")
      .withStages(stageType, stageType, "join")
      .withParallel(true)
      .build()
  }

  @Issue("SPIN-2129")
  // Stages such as Canary with no tasks of their own (only synthetic stages)
  // failed to join correctly as they remain NOT_STARTED
  def "should support pipelines where a parent and child stage share requisiteRefIds"() {
    given:
    execution.with {
      stages[0].refId = "1"
      stages[0].requisiteStageRefIds = []

      stages[1].refId = "2"
      stages[1].requisiteStageRefIds = ["1"]

      stages[2].refId = "3"
      stages[2].requisiteStageRefIds = ["1"]

      stages[3].refId = "4"
      stages[3].requisiteStageRefIds = ["1", "2"]
    }

    and:
    executionRepository.retrievePipeline(execution.id) >> execution

    and:
    def noOpStageDefinitionBuilder = Stub(StageDefinitionBuilder) {
      getType() >> stageType
      buildTaskGraph(_) >> TaskNode.emptyGraph(FULL)
    }
    def joinStageDefinitionBuilder = Stub(StageDefinitionBuilder) {
      getType() >> "join"
      buildTaskGraph(_) >> TaskNode.singleton(FULL, "test", TestTask)
    }

    def runner = create(noOpStageDefinitionBuilder, joinStageDefinitionBuilder)

    when:
    runner.start(execution)

    then:
    1 * testTask.execute(_) >> new TaskResult(SUCCEEDED)
    1 * executionListener.afterExecution(_, execution, SUCCEEDED, true)

    where:
    stageType = "noop"
    execution = Pipeline
      .builder()
      .withId("1")
      .withStages(stageType, stageType, stageType, "join")
      .withParallel(true)
      .build()
  }

  static Stage<Pipeline> before(Stage<Pipeline> stage) {
    stage.syntheticStageOwner = SyntheticStageOwner.STAGE_BEFORE
    return stage
  }

  static Stage<Pipeline> after(Stage<Pipeline> stage) {
    stage.syntheticStageOwner = SyntheticStageOwner.STAGE_AFTER
    return stage
  }

  @CompileStatic
  static interface TestTask extends Task {}

  @CompileStatic
  static interface PreLoopTask extends Task {}

  @CompileStatic
  static interface StartLoopTask extends Task {}

  @CompileStatic
  static interface EndLoopTask extends Task {}

  @CompileStatic
  static interface PostLoopTask extends Task {}

  @CompileStatic
  static StageDefinitionBuilder stageDefinition(String name, Consumer<TaskNode.Builder> closure) {
    return new StageDefinitionBuilder() {
      @Override
      public <T extends Execution<T>> void taskGraph(Stage<T> stage, TaskNode.Builder builder) {
        closure.accept(builder)
      }

      @Override
      String getType() {
        name
      }
    }
  }

  @CompileStatic
  static BranchingStageDefinitionBuilder stageDefinition(
    String name,
    Collection<Map<String, Object>> contexts,
    Consumer<TaskNode.Builder> before,
    Consumer<TaskNode.Builder> branch,
    Consumer<TaskNode.Builder> after,
    BiFunction<Stage, Boolean, String> parallelStageName = { s, b -> name }) {
    return new BranchingStageDefinitionBuilder() {
      @Override
      public <T extends Execution<T>> void taskGraph(Stage<T> stage, TaskNode.Builder builder) {
        branch.accept(builder)
      }

      public <T extends Execution<T>> void preBranchGraph(Stage<T> stage, TaskNode.Builder builder) {
        before.accept(builder)
      }

      public <T extends Execution<T>> void postBranchGraph(Stage<T> stage, TaskNode.Builder builder) {
        after.accept(builder)
      }

      public <T extends Execution<T>> Collection<Map<String, Object>> parallelContexts(Stage<T> stage) {
        contexts.collect {
          it + [type: getType(), name: it.name ?: getType()]
        }
      }

      @Override
      String parallelStageName(Stage stage, boolean hasParallelFlows) {
        parallelStageName.apply(stage, hasParallelFlows)
      }

      @Override
      String getType() {
        name
      }
    }
  }

  @CompileStatic
  static class Config {
    @Bean
    FactoryBean<ExecutionRepository> executionRepository() {
      new SpockMockFactoryBean(ExecutionRepository)
    }

    @Bean
    FactoryBean<TestTask> testTask() { new SpockMockFactoryBean(TestTask) }

    @Bean
    FactoryBean<PreLoopTask> preLoopTask() {
      new SpockMockFactoryBean(PreLoopTask)
    }

    @Bean
    FactoryBean<StartLoopTask> startLoopTask() {
      new SpockMockFactoryBean(StartLoopTask)
    }

    @Bean
    FactoryBean<EndLoopTask> endLoopTask() {
      new SpockMockFactoryBean(EndLoopTask)
    }

    @Bean
    FactoryBean<PostLoopTask> postLoopTask() {
      new SpockMockFactoryBean(PostLoopTask)
    }

    @Bean
    @Qualifier("stageListener")
    FactoryBean<StageListener> stageListener() {
      new SpockMockFactoryBean(StageListener)
    }

    @Bean
    @Qualifier("executionListener")
    FactoryBean<ExecutionListener> executionListener() {
      new SpockMockFactoryBean(ExecutionListener)
    }

    @Bean
    ContextParameterProcessor contextParameterProcessor() {
      new ContextParameterProcessor()
    }
  }

  private static final <T> Closure<T> noOp() { return {} as Closure<T> }
}
