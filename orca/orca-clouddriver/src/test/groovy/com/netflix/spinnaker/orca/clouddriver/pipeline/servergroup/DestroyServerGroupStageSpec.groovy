package com.netflix.spinnaker.orca.clouddriver.pipeline.servergroup

import com.netflix.spinnaker.kork.dynamicconfig.DynamicConfigService
import com.netflix.spinnaker.orca.api.pipeline.graph.StageDefinitionBuilder
import com.netflix.spinnaker.orca.api.pipeline.graph.TaskNode
import com.netflix.spinnaker.orca.api.pipeline.models.ExecutionType
import com.netflix.spinnaker.orca.pipeline.graph.StageGraphBuilderImpl
import com.netflix.spinnaker.orca.pipeline.model.PipelineExecutionImpl
import com.netflix.spinnaker.orca.pipeline.model.StageExecutionImpl
import spock.lang.Specification
import spock.lang.Unroll

class DestroyServerGroupStageSpec extends Specification {

  DynamicConfigService dynamicConfigService = Mock(DynamicConfigService)
  DestroyServerGroupStage destroyServerGroupStage = new DestroyServerGroupStage(dynamicConfigService)
  PipelineExecutionImpl pipelineExecution = new PipelineExecutionImpl(ExecutionType.PIPELINE, 'foo')
  def context = [
        credentials           : 'test',
        cluster: 'foo-main',
        moniker               : [
            app: 'foo',
            cluster: 'foo-main',
            stack: 'main'],
        regions               : ['us-west-2'],
        target: 'current_asg_dynamic',
    ]
  StageExecutionImpl parentStage = new StageExecutionImpl(pipelineExecution, "destroyServerGroup", context)

  @Unroll
  def "post-destroy cache refresh for #cloudProvider with flag enabled=#enabled produces task order #expectedTaskNames"() {
    given:
    dynamicConfigService.isEnabled("stages.destroy-server-group-stage.force-cache-refresh.enabled", true) >> enabled
    def stage = new StageExecutionImpl(pipelineExecution, "destroyServerGroup", [
      credentials   : 'test',
      cloudProvider : cloudProvider,
    ])

    when:
    def graph = TaskNode.build(TaskNode.GraphType.FULL) {
      destroyServerGroupStage.taskGraphInternal(stage, it)
    }

    then:
    graph*.name == expectedTaskNames

    where:
    cloudProvider | enabled || expectedTaskNames
    "gce"         | true    || ["destroyServerGroup", "monitorServerGroup", "forceCacheRefresh", "waitForDestroyedServerGroup"]
    "gce"         | false   || ["destroyServerGroup", "monitorServerGroup", "waitForDestroyedServerGroup"]
    "aws"         | true    || ["destroyServerGroup", "monitorServerGroup", "waitForDestroyedServerGroup"]
    "aws"         | false   || ["destroyServerGroup", "monitorServerGroup", "waitForDestroyedServerGroup"]
  }

  def "should add disable stage to graph when skipDisableBeforeDestroy is false"() {
    given:
    def context = [skipDisableBeforeDestroy: false]
    def graph = StageGraphBuilderImpl.beforeStages(parentStage)

    when:
    destroyServerGroupStage.preStatic(context, graph)

    then:
    def stages = graph.build().collect { it }
    stages.size() == 1
    def stage = stages.first()
    stage.name == "disableServerGroup"
    stage.type == StageDefinitionBuilder.getType(DisableServerGroupStage)
    stage.context == context
  }

  def "should add disable stage in preStatic method"() {
    given:
    def context = [skipDisableBeforeDestroy: false]
    def graph = StageGraphBuilderImpl.beforeStages(parentStage)

    when:
    destroyServerGroupStage.preStatic(context, graph)

    then:
    def stages = graph.build().collect { it }
    stages.size() == 1
    def stage = stages.first()
    stage.name == "disableServerGroup"
    stage.type == StageDefinitionBuilder.getType(DisableServerGroupStage)
    stage.context == context
  }

  def "should add disable stage in preDynamic method"() {
    given:
    def context = [skipDisableBeforeDestroy: false]
    def graph = StageGraphBuilderImpl.beforeStages(parentStage)

    when:
    destroyServerGroupStage.preDynamic(context, graph)

    then:
    def stages = graph.build().collect { it }
    stages.size() == 1
    def stage = stages.first()
    stage.name == "disableServerGroup"
    stage.type == StageDefinitionBuilder.getType(DisableServerGroupStage)
    stage.context == context
  }

  def "should not add disable stage in preStatic method when skipDisableBeforeDestroy is true"() {
    given:
    def context = [skipDisableBeforeDestroy: true]
    def graph = StageGraphBuilderImpl.beforeStages(parentStage)

    when:
    destroyServerGroupStage.preStatic(context, graph)

    then:
    graph.build().size() == 0
  }

  def "should not add disable stage in preDynamic method when skipDisableBeforeDestroy is true"() {
    given:
    def context = [skipDisableBeforeDestroy: true]
    def graph = StageGraphBuilderImpl.beforeStages(parentStage)

    when:
    destroyServerGroupStage.preDynamic(context, graph)

    then:
    graph.build().size() == 0
  }
}
