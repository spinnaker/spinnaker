package com.netflix.spinnaker.orca.kayenta.tasks

import com.netflix.spinnaker.kork.exceptions.UserException
import com.netflix.spinnaker.kork.retrofit.Retrofit2SyncCall
import com.netflix.spinnaker.orca.api.pipeline.Task
import com.netflix.spinnaker.orca.api.pipeline.TaskResult
import com.netflix.spinnaker.orca.api.pipeline.models.ExecutionStatus.SUCCEEDED
import com.netflix.spinnaker.orca.api.pipeline.models.StageExecution
import com.netflix.spinnaker.orca.ext.mapTo
import com.netflix.spinnaker.orca.kayenta.KayentaService
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

@Component
class ResolveKayentaConfigIdTask(
  private val kayentaService: KayentaService
) : Task {

  private val log = LoggerFactory.getLogger(javaClass)

  override fun execute(stage: StageExecution): TaskResult {
    val configName = stage.mapTo<String?>("/canaryConfigName")
    val currentApplication = stage.execution.application
    val canaryConfigList = Retrofit2SyncCall.execute(kayentaService.getAllCanaryConfigs())
    val candidates = canaryConfigList.asSequence()
      .filter { it.name == configName && it.applications.contains(currentApplication) }
      .toList()

    if (candidates.size == 0) {
      throw UserException("Couldn't find a canary configId for configName $configName and application $currentApplication")
    } else if (candidates.size > 1) {
      throw UserException("Found more than one canary configId for configName $configName and application $currentApplication")
    }
    return TaskResult.builder(SUCCEEDED).context("canaryConfigId", candidates[0].id).build()
  }
}
