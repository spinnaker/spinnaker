package com.netflix.spinnaker.orca.q

import com.netflix.spinnaker.orca.api.pipeline.Task
import com.netflix.spinnaker.orca.api.pipeline.graph.StageDefinitionBuilder
import org.springframework.beans.factory.ObjectProvider

class StageDefinitionBuildersProvider(
  private val stageDefinitionBuilders: Collection<StageDefinitionBuilder>
) : ObjectProvider<Collection<StageDefinitionBuilder>> {
  override fun getIfUnique(): Collection<StageDefinitionBuilder>? = stageDefinitionBuilders
  override fun getObject(vararg args: Any?): Collection<StageDefinitionBuilder> = stageDefinitionBuilders
  override fun getObject(): Collection<StageDefinitionBuilder> = stageDefinitionBuilders
  override fun getIfAvailable(): Collection<StageDefinitionBuilder>? = stageDefinitionBuilders
}

class TasksProvider(
  private val tasks: Collection<Task>
) : ObjectProvider<Collection<Task>> {
  override fun getIfUnique(): Collection<Task>? = tasks
  override fun getObject(vararg args: Any?): Collection<Task> = tasks
  override fun getObject(): Collection<Task> = tasks
  override fun getIfAvailable(): Collection<Task>? = tasks
}
